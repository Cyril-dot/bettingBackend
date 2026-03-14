package com.bettingPlatform.BettingWebsite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateAdminRequest {

    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    private String currentPassword;

    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    private String confirmNewPassword;
}