package com.customersupport.backend.dto;

import com.customersupport.backend.enums.MetricsPeriod;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dashboard metrics for tickets created in [from, to]. Metric definitions are documented in CLAUDE.md. */
public record DashboardMetrics(MetricsPeriod period,
                               Instant from,
                               Instant to,
                               long totalTickets,
                               List<BucketCount> ticketsOverTime,
                               List<StatusCount> statusCounts,
                               List<CategoryCount> categoryCounts,
                               HandledCounts handled,
                               Long avgResponseSeconds,
                               Resolution resolution) {

    /** bucketStart is the day (DAILY) or the Monday of the ISO week (WEEKLY), in server-local time. */
    public record BucketCount(LocalDate bucketStart, long count) {
    }

    public record StatusCount(TicketStatus status, long count) {
    }

    /** A null category means the ticket hasn't been categorized yet. */
    public record CategoryCount(TicketCategory category, long count) {
    }

    /** Every ticket is in exactly one group; see MetricsService.handledBy for the rules. */
    public record HandledCounts(long ai, long human, long unhandled) {
    }

    public record Resolution(long resolved, long total, Double rate) {
    }
}
