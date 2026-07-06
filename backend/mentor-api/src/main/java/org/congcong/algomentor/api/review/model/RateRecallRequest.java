package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.NotBlank;

public record RateRecallRequest(@NotBlank String rating) {
}
