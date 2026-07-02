package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;

public record ReviewRecallHistoryItem(
    long id,
    ReviewGrade grade,
    String userRecallText,
    String userNoteTransient,
    Instant reviewedAt,
    int intervalAfter
) {
}
