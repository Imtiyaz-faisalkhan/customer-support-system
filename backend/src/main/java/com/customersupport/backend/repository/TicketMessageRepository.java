package com.customersupport.backend.repository;

import com.customersupport.backend.entity.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {

    boolean existsByExternalMessageId(String externalMessageId);

    List<TicketMessage> findByExternalMessageIdIn(Collection<String> externalMessageIds);
}
