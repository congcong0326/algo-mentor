package org.congcong.algomentor.api.review.mapper.model;

import java.time.Instant;

public record ReviewRecallHistoryRow(
    long id,
    int grade,
    String userRecallText,
    String userNoteTransient,
    Instant reviewedAt,
    int intervalAfter
) {
}
