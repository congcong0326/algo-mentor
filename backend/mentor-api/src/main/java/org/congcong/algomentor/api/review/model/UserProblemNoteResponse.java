package org.congcong.algomentor.api.review.model;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;

public record UserProblemNoteResponse(
    Long id,
    String problemSlug,
    ProblemSolutionOutlineV1 outline,
    String noteMarkdown,
    long revision,
    long coachSummaryRevision,
    boolean exists,
    boolean hasContent,
    Instant createdAt,
    Instant coachSummaryUpdatedAt,
    Instant updatedAt
) {
}
