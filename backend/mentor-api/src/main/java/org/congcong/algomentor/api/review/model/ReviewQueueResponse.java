package org.congcong.algomentor.api.review.model;

import java.util.List;

public record ReviewQueueResponse(List<MistakeNoteResponse> items, int dueCount) {
}
