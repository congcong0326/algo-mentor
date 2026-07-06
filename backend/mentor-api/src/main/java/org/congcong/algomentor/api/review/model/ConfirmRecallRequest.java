package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRecallRequest(
    long evaluationId,
    @NotBlank String rating
) {
}
