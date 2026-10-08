package com.customersupport.backend.controller;

import com.customersupport.backend.dto.MessageDto;
import com.customersupport.backend.dto.PageResponse;
import com.customersupport.backend.dto.ReplyRequest;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.dto.TicketSummary;
import com.customersupport.backend.dto.UpdateStatusRequest;
import com.customersupport.backend.enums.TicketCategory;
import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public PageResponse<TicketSummary> list(@RequestParam(required = false) TicketStatus status,
                                            @RequestParam(required = false) TicketCategory category,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ticketService.list(status, category, pageable);
    }

    @GetMapping("/{id}")
    public TicketDetail get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @PatchMapping("/{id}/status")
    public TicketSummary updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ticketService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest request) {
        return ticketService.reply(id, request.body());
    }
}
