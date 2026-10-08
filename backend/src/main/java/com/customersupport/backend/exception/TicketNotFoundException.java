package com.customersupport.backend.exception;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Long id) {
        super("Ticket " + id + " not found");
    }
}
