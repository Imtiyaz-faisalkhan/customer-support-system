package com.customersupport.backend.service;

import com.customersupport.backend.dto.ApiTimestamps;
import com.customersupport.backend.dto.DashboardMetrics;
import com.customersupport.backend.dto.DashboardMetrics.BucketCount;
import com.customersupport.backend.dto.DashboardMetrics.CategoryCount;
import com.customersupport.backend.dto.DashboardMetrics.HandledCounts;
import com.customersupport.backend.dto.DashboardMetrics.Resolution;
import com.customersupport.backend.dto.DashboardMetrics.StatusCount;
import com.customersupport.backend.dto.TicketMetricRow;
import com.customersupport.backend.enums.MetricsPeriod;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes dashboard metrics over tickets created in the selected window. Aggregation happens in Java over one
 * lightweight row per ticket, which keeps the query DB-agnostic (no date functions); fine at MVP volume.
 */
@Service
@Transactional(readOnly = true)
public class MetricsService {

    private static final int DAILY_BUCKETS = 30;
    private static final int WEEKLY_BUCKETS = 12;

    private final TicketRepository ticketRepository;
    private final Clock clock;

    public MetricsService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    public DashboardMetrics getMetrics(MetricsPeriod period) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDate> bucketStarts = bucketStarts(period, now.toLocalDate());
        LocalDateTime from = bucketStarts.getFirst().atStartOfDay();
        List<TicketMetricRow> rows = ticketRepository.findMetricRowsCreatedSince(from);

        return new DashboardMetrics(period, ApiTimestamps.toInstant(from), ApiTimestamps.toInstant(now),
                rows.size(), ticketsOverTime(period, bucketStarts, rows), statusCounts(rows),
                categoryCounts(rows), handledCounts(rows), avgResponseSeconds(rows), resolution(rows));
    }

    private static List<LocalDate> bucketStarts(MetricsPeriod period, LocalDate today) {
        List<LocalDate> starts = new ArrayList<>();
        if (period == MetricsPeriod.DAILY) {
            for (int i = DAILY_BUCKETS - 1; i >= 0; i--) {
                starts.add(today.minusDays(i));
            }
        } else {
            LocalDate currentWeek = weekStart(today);
            for (int i = WEEKLY_BUCKETS - 1; i >= 0; i--) {
                starts.add(currentWeek.minusWeeks(i));
            }
        }
        return starts;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static List<BucketCount> ticketsOverTime(MetricsPeriod period, List<LocalDate> bucketStarts,
                                                     List<TicketMetricRow> rows) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        bucketStarts.forEach(start -> counts.put(start, 0L));
        for (TicketMetricRow row : rows) {
            LocalDate day = row.createdAt().toLocalDate();
            counts.computeIfPresent(period == MetricsPeriod.DAILY ? day : weekStart(day), (k, v) -> v + 1);
        }
        return counts.entrySet().stream().map(e -> new BucketCount(e.getKey(), e.getValue())).toList();
    }

    private static List<StatusCount> statusCounts(List<TicketMetricRow> rows) {
        return Arrays.stream(TicketStatus.values())
                .map(status -> new StatusCount(status, rows.stream().filter(r -> r.status() == status).count()))
                .toList();
    }

    private static List<CategoryCount> categoryCounts(List<TicketMetricRow> rows) {
        List<TicketCategory> categories = new ArrayList<>(Arrays.asList(TicketCategory.values()));
        categories.add(null); // uncategorized, always listed last
        return categories.stream()
                .map(category -> new CategoryCount(category,
                        rows.stream().filter(r -> Objects.equals(r.category(), category)).count()))
                .toList();
    }

    private enum HandledBy { AI, HUMAN, UNHANDLED }

    private static HandledCounts handledCounts(List<TicketMetricRow> rows) {
        Map<HandledBy, Long> counts = new EnumMap<>(HandledBy.class);
        rows.forEach(row -> counts.merge(handledBy(row), 1L, Long::sum));
        return new HandledCounts(counts.getOrDefault(HandledBy.AI, 0L), counts.getOrDefault(HandledBy.HUMAN, 0L),
                counts.getOrDefault(HandledBy.UNHANDLED, 0L));
    }

    /**
     * AI = the AI replied, no admin ever did, and the ticket was never escalated (AI_RESPONDED or RESOLVED).
     * Unhandled = still OPEN. Human = everything else: an admin replied, the ticket is escalated
     * (PENDING_HUMAN / IN_PROGRESS — "AI could not resolve"), or the admin closed it without any reply.
     */
    private static HandledBy handledBy(TicketMetricRow row) {
        boolean adminReplied = Boolean.TRUE.equals(row.hasAdminReply());
        if (row.status() == TicketStatus.OPEN && !adminReplied) {
            return HandledBy.UNHANDLED;
        }
        boolean aiOnly = row.firstResponseAt() != null && !adminReplied;
        boolean notEscalated = row.status() == TicketStatus.AI_RESPONDED || row.status() == TicketStatus.RESOLVED;
        return aiOnly && notEscalated ? HandledBy.AI : HandledBy.HUMAN;
    }

    private static Long avgResponseSeconds(List<TicketMetricRow> rows) {
        List<Long> seconds = rows.stream()
                .filter(r -> r.firstResponseAt() != null)
                .map(r -> Duration.between(ApiTimestamps.toInstant(r.createdAt()), r.firstResponseAt()).toSeconds())
                .toList();
        if (seconds.isEmpty()) {
            return null;
        }
        return Math.round(seconds.stream().mapToLong(Long::longValue).average().orElseThrow());
    }

    private static Resolution resolution(List<TicketMetricRow> rows) {
        long resolved = rows.stream().filter(r -> r.status() == TicketStatus.RESOLVED).count();
        long total = rows.size();
        return new Resolution(resolved, total, total == 0 ? null : (double) resolved / total);
    }
}
