package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitReviewAttemptRequest(
    @NotNull UUID clientAttemptId,
    @NotBlank String rating
) {
}
