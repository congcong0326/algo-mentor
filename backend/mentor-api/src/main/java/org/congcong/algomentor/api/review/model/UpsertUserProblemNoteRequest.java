package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;

public record UpsertUserProblemNoteRequest(
    @NotNull ProblemSolutionOutlineV1 outline,
    @Min(0) long expectedRevision
) {
}
