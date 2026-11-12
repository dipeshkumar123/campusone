package com.campusone.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Enter your email.")
        @Email(message = "Enter a valid email address.")
        String email,

        @NotBlank(message = "Enter your password.")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters.")
        String password
) {
}
