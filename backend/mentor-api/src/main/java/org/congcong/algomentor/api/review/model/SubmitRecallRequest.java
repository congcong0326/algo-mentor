package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.NotBlank;

public record SubmitRecallRequest(@NotBlank String recallText, String transientNote) {
}
