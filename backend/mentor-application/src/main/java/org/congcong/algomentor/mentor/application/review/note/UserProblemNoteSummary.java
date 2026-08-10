package org.congcong.algomentor.mentor.application.review.note;

import java.time.Instant;

/** 不包含 Markdown 正文的题目笔记摘要，供只读状态查询使用。 */
public record UserProblemNoteSummary(
    Long id,
    long userId,
    String problemSlug,
    ProblemSolutionOutlineV1 outline,
    boolean hasNoteMarkdown,
    long revision,
    long coachSummaryRevision,
    Instant createdAt,
    Instant coachSummaryUpdatedAt,
    Instant updatedAt
) {

  public UserProblemNoteSummary(
      Long id,
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      boolean hasNoteMarkdown,
      long revision,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(
        id,
        userId,
        problemSlug,
        outline,
        hasNoteMarkdown,
        revision,
        hasNoteMarkdown ? 1 : 0,
        createdAt,
        hasNoteMarkdown ? updatedAt : null,
        updatedAt);
  }

  public UserProblemNoteSummary {
    outline = outline == null ? ProblemSolutionOutlineV1.empty() : outline;
    revision = Math.max(0, revision);
    coachSummaryRevision = Math.max(0, coachSummaryRevision);
  }

  public static UserProblemNoteSummary from(UserProblemNote note) {
    return new UserProblemNoteSummary(
        note.id(),
        note.userId(),
        note.problemSlug(),
        note.outline(),
        !note.noteMarkdown().isBlank(),
        note.revision(),
        note.coachSummaryRevision(),
        note.createdAt(),
        note.coachSummaryUpdatedAt(),
        note.updatedAt());
  }

  public boolean exists() {
    return id != null;
  }

  public boolean hasContent() {
    return outline.hasContent() || hasNoteMarkdown;
  }
}
