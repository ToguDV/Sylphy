package com.togudv.sylphy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemPromptUpdateDTO(
        @NotBlank
        @Size(max = 10000)
        String content
) {}
