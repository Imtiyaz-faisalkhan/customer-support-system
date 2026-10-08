package com.customersupport.backend.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Ticket.createdAt/updatedAt are zone-less LocalDateTimes generated in the JVM's default zone. The API sends
 * every timestamp as a UTC Instant instead, so clients never have to guess the server's zone and all
 * timestamps in a response (message times, firstResponseAt, ...) are directly comparable.
 */
final class ApiTimestamps {

    private ApiTimestamps() {
    }

    static Instant toInstant(LocalDateTime serverLocal) {
        return serverLocal == null ? null : serverLocal.atZone(ZoneId.systemDefault()).toInstant();
    }
}
