package com.customersupport.backend.dto;

public record OutboundEmail(String to, String subject, String body) {
}
