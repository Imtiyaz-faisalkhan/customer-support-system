package com.customersupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/** The fields we use from Resend's GET /emails/receiving/{id} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendReceivedEmail(String id,
                                  String from,
                                  String subject,
                                  String text,
                                  String html,
                                  Map<String, Object> headers,
                                  @JsonProperty("message_id") String messageId,
                                  List<Map<String, Object>> attachments) {
}
