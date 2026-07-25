package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.NotBlank;

public record CreateReviewCardRequest(@NotBlank String problemSlug) {
}
