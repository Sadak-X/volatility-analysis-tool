package com.volatility.modules.auth.dto;

public record UserProfile(
        Long id,
        String username,
        String nickname,
        String roleCode
) {
}
