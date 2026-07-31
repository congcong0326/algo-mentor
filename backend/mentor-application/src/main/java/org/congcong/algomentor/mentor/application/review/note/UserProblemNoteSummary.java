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
    Instant createdAt,
    Instant updatedAt
) {

  public UserProblemNoteSummary {
    outline = outline == null ? ProblemSolutionOutlineV1.empty() : outline;
    revision = Math.max(0, revision);
  }

  public static UserProblemNoteSummary from(UserProblemNote note) {
    return new UserProblemNoteSummary(
        note.id(),
        note.userId(),
        note.problemSlug(),
        note.outline(),
        !note.noteMarkdown().isBlank(),
        note.revision(),
        note.createdAt(),
        note.updatedAt());
  }

  public boolean exists() {
    return id != null;
  }

  public boolean hasContent() {
    return outline.hasContent() || hasNoteMarkdown;
  }
}
