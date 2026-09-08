package com.volatility.modules.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record AiFollowupRequest(
        @NotBlank(message = "追问内容不能为空") String question,
        List<AiFollowupMessage> history
) {
    public record AiFollowupMessage(
            String role,
            String content
    ) {
    }
}
