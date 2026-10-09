package com.customersupport.backend.exception;

public class UndeliverableRecipientException extends RuntimeException {

    public UndeliverableRecipientException(String message) {
        super(message);
    }
}
