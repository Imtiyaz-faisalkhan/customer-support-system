package com.customersupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The 1000-character limit is mirrored by MAX_REPLY_LENGTH in frontend TicketDetailPage.jsx; keep them in sync. */
public record ReplyRequest(@NotBlank @Size(max = 1000) String body) {
}
