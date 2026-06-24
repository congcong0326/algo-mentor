package org.congcong.algomentor.api.practice.model;

import jakarta.validation.constraints.NotBlank;

public record PracticeMessageRequest(@NotBlank String message) {
}
