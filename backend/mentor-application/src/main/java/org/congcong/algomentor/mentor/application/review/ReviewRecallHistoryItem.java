package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;

public record ReviewRecallHistoryItem(
    long id,
    ReviewRating rating,
    String userRecallText,
    String userNoteTransient,
    Instant reviewedAt,
    int intervalAfter
) {
}
