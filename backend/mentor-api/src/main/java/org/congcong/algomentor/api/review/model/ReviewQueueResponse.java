package org.congcong.algomentor.api.review.model;

import java.util.List;

public record ReviewQueueResponse(List<ReviewCardResponse> items, int dueCount) {
}
