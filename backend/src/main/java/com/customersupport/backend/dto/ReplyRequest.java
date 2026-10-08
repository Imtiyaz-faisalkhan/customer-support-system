package com.customersupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyRequest(@NotBlank @Size(max = 10_000) String body) {
}
