package org.congcong.algomentor.api.review.model;

import java.util.List;

public record ReviewCardContextResponse(
    ReviewCardResponse card,
    ReviewProblemResponse problem,
    UserProblemNoteResponse note,
    List<ReviewAttemptResponse> recentAttempts,
    List<ReviewIntervalPreviewResponse> intervalPreviews
) {
}
