package org.congcong.algomentor.api.review.model;

import java.time.Instant;

public record ReviewRecallHistoryResponse(
    long id,
    String rating,
    String userRecallText,
    String userNoteTransient,
    Instant reviewedAt,
    int intervalAfter
) {
}
