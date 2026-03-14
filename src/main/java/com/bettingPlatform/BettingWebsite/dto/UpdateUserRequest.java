package com.bettingPlatform.BettingWebsite.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String phoneNumber;

    // Required only when changing password
    private String currentPassword;

    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;

    private String confirmNewPassword;
}