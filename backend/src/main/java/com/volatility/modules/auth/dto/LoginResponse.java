package com.volatility.modules.auth.dto;

public record LoginResponse(
        String token,
        UserProfile user
) {
}
