package org.congcong.algomentor.api.review.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;

public record UpsertUserProblemNoteRequest(
    @NotNull ProblemSolutionOutlineV1 outline,
    @Size(max = 10000) String noteMarkdown,
    @Min(0) long expectedRevision
) {
}
