package org.congcong.algomentor.mentor.application.review.note;

import java.time.Instant;

public record UserProblemNote(
    Long id,
    long userId,
    String problemSlug,
    ProblemSolutionOutlineV1 outline,
    String noteMarkdown,
    long revision,
    Instant createdAt,
    Instant updatedAt
) {
  public UserProblemNote {
    outline = outline == null ? ProblemSolutionOutlineV1.empty() : outline;
    noteMarkdown = noteMarkdown == null ? "" : noteMarkdown;
    revision = Math.max(0, revision);
  }

  public static UserProblemNote empty(long userId, String problemSlug) {
    return new UserProblemNote(null, userId, problemSlug, ProblemSolutionOutlineV1.empty(), "", 0, null, null);
  }

  public boolean exists() {
    return id != null;
  }

  public boolean hasContent() {
    return outline.hasContent() || !noteMarkdown.isBlank();
  }
}
