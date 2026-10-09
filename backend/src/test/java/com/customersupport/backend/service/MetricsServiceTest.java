package com.customersupport.backend.service;

import com.customersupport.backend.dto.DashboardMetrics;
import com.customersupport.backend.dto.DashboardMetrics.BucketCount;
import com.customersupport.backend.dto.DashboardMetrics.CategoryCount;
import com.customersupport.backend.dto.DashboardMetrics.StatusCount;
import com.customersupport.backend.dto.TicketMetricRow;
import com.customersupport.backend.enums.MetricsPeriod;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    // Thursday 2026-10-08, 12:00 server-local; its ISO week starts Monday 2026-10-05.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private TicketRepository ticketRepository;

    private MetricsService service;

    @BeforeEach
    void setUp() {
        service = new MetricsService(ticketRepository, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private static Instant plusSeconds(LocalDateTime createdAt, long seconds) {
        return createdAt.atZone(ZONE).toInstant().plusSeconds(seconds);
    }

    private static List<TicketMetricRow> sampleRows() {
        LocalDateTime today = LocalDateTime.of(2026, 10, 8, 9, 0);
        LocalDateTime tuesday = LocalDateTime.of(2026, 10, 6, 10, 0);
        LocalDateTime lastSunday = LocalDateTime.of(2026, 10, 4, 10, 0); // previous ISO week
        return List.of(
                // AI replied after 2 minutes, no admin reply.
                new TicketMetricRow(today, plusSeconds(today, 120), TicketStatus.AI_RESPONDED,
                        TicketCategory.BILLING, false),
                // Admin replied after 1 hour, then resolved.
                new TicketMetricRow(tuesday, plusSeconds(tuesday, 3600), TicketStatus.RESOLVED,
                        TicketCategory.TECHNICAL, true),
                // No reply yet, not categorized.
                new TicketMetricRow(lastSunday, null, TicketStatus.OPEN, null, false));
    }

    @Test
    void daily_buckets30DaysEndingToday_andComputesAllMetrics() {
        when(ticketRepository.findMetricRowsCreatedSince(LocalDateTime.of(2026, 9, 9, 0, 0)))
                .thenReturn(sampleRows());

        DashboardMetrics metrics = service.getMetrics(MetricsPeriod.DAILY);

        assertThat(metrics.totalTickets()).isEqualTo(3);
        assertThat(metrics.ticketsOverTime()).hasSize(30);
        assertThat(metrics.ticketsOverTime().getFirst()).isEqualTo(new BucketCount(LocalDate.of(2026, 9, 9), 0));
        assertThat(metrics.ticketsOverTime().getLast()).isEqualTo(new BucketCount(LocalDate.of(2026, 10, 8), 1));
        assertThat(metrics.ticketsOverTime()).contains(
                new BucketCount(LocalDate.of(2026, 10, 6), 1), new BucketCount(LocalDate.of(2026, 10, 4), 1));

        assertThat(metrics.statusCounts()).containsExactly(
                new StatusCount(TicketStatus.OPEN, 1),
                new StatusCount(TicketStatus.AI_RESPONDED, 1),
                new StatusCount(TicketStatus.PENDING_HUMAN, 0),
                new StatusCount(TicketStatus.IN_PROGRESS, 0),
                new StatusCount(TicketStatus.RESOLVED, 1));
        assertThat(metrics.categoryCounts()).containsExactly(
                new CategoryCount(TicketCategory.BILLING, 1),
                new CategoryCount(TicketCategory.TECHNICAL, 1),
                new CategoryCount(TicketCategory.GENERAL, 0),
                new CategoryCount(null, 1));

        assertThat(metrics.handled().ai()).isEqualTo(1);
        assertThat(metrics.handled().human()).isEqualTo(1);
        assertThat(metrics.handled().unhandled()).isEqualTo(1);
        assertThat(metrics.avgResponseSeconds()).isEqualTo((120 + 3600) / 2);
        assertThat(metrics.resolution().resolved()).isEqualTo(1);
        assertThat(metrics.resolution().rate()).isEqualTo(1.0 / 3);
    }

    @Test
    void handledSplit_countsEscalatedTicketsAsHuman() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 7, 9, 0);
        when(ticketRepository.findMetricRowsCreatedSince(LocalDateTime.of(2026, 9, 9, 0, 0))).thenReturn(List.of(
                // AI replied and the customer was satisfied -> AI.
                new TicketMetricRow(t, plusSeconds(t, 60), TicketStatus.RESOLVED, null, false),
                // AI replied, customer pushed back, escalated; no admin reply yet -> human (AI couldn't resolve).
                new TicketMetricRow(t, plusSeconds(t, 60), TicketStatus.PENDING_HUMAN, null, false),
                // Escalated straight away with no reply at all -> human.
                new TicketMetricRow(t, null, TicketStatus.IN_PROGRESS, null, false),
                // Admin closed it without anyone replying (e.g. spam) -> human.
                new TicketMetricRow(t, null, TicketStatus.RESOLVED, null, false),
                // Nobody has looked at it yet -> unhandled.
                new TicketMetricRow(t, null, TicketStatus.OPEN, null, false)));

        DashboardMetrics.HandledCounts handled = service.getMetrics(MetricsPeriod.DAILY).handled();

        assertThat(handled.ai()).isEqualTo(1);
        assertThat(handled.human()).isEqualTo(3);
        assertThat(handled.unhandled()).isEqualTo(1);
    }

    @Test
    void weekly_bucketsByIsoWeekStartingMonday() {
        // 12 weeks ending with the current week (Mon 2026-10-05) start on Mon 2026-07-20.
        when(ticketRepository.findMetricRowsCreatedSince(LocalDateTime.of(2026, 7, 20, 0, 0)))
                .thenReturn(sampleRows());

        List<BucketCount> buckets = service.getMetrics(MetricsPeriod.WEEKLY).ticketsOverTime();

        assertThat(buckets).hasSize(12);
        assertThat(buckets.getFirst().bucketStart()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(buckets.getLast()).isEqualTo(new BucketCount(LocalDate.of(2026, 10, 5), 2));
        assertThat(buckets.get(10)).isEqualTo(new BucketCount(LocalDate.of(2026, 9, 28), 1)); // the Sunday ticket
    }

    @Test
    void emptyWindow_hasZeroCountsAndNullRates() {
        when(ticketRepository.findMetricRowsCreatedSince(LocalDateTime.of(2026, 9, 9, 0, 0))).thenReturn(List.of());

        DashboardMetrics metrics = service.getMetrics(MetricsPeriod.DAILY);

        assertThat(metrics.totalTickets()).isZero();
        assertThat(metrics.ticketsOverTime()).hasSize(30).allMatch(bucket -> bucket.count() == 0);
        assertThat(metrics.avgResponseSeconds()).isNull();
        assertThat(metrics.resolution().rate()).isNull();
        verify(ticketRepository).findMetricRowsCreatedSince(LocalDateTime.of(2026, 9, 9, 0, 0));
    }
}
