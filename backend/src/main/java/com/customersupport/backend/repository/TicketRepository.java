package com.customersupport.backend.repository;

import com.customersupport.backend.dto.TicketMetricRow;
import com.customersupport.backend.entity.Ticket;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:category IS NULL OR t.category = :category)
            """)
    Page<Ticket> search(@Param("status") TicketStatus status,
                        @Param("category") TicketCategory category,
                        Pageable pageable);

    @Query("""
            SELECT new com.customersupport.backend.dto.TicketMetricRow(
                t.createdAt, t.firstResponseAt, t.status, t.category,
                CASE WHEN EXISTS (
                    SELECT 1 FROM TicketMessage m
                    WHERE m.ticket = t AND m.author = com.customersupport.backend.enums.MessageAuthor.ADMIN
                ) THEN true ELSE false END)
            FROM Ticket t
            WHERE t.createdAt >= :from
            """)
    List<TicketMetricRow> findMetricRowsCreatedSince(@Param("from") LocalDateTime from);
}
