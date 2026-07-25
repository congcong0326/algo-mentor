package org.congcong.algomentor.mentor.application.review.note;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;

public class UserProblemNoteService {

  private final UserProblemNoteRepository repository;
  private final ReviewProblemCatalog problemCatalog;
  private final Clock clock;

  public UserProblemNoteService(
      UserProblemNoteRepository repository,
      ReviewProblemCatalog problemCatalog,
      Clock clock
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problemCatalog must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public UserProblemNote get(long userId, String problemSlug) {
    String slug = requireProblem(problemSlug);
    return repository.find(userId, slug).orElseGet(() -> UserProblemNote.empty(userId, slug));
  }

  public UserProblemNote upsert(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      String noteMarkdown,
      long expectedRevision
  ) {
    String slug = requireProblem(problemSlug);
    ProblemSolutionOutlineV1 normalizedOutline = outline == null ? ProblemSolutionOutlineV1.empty() : outline;
    String normalizedMarkdown = normalizeMarkdown(noteMarkdown);
    Instant now = Instant.now(clock);
    if (expectedRevision == 0) {
      return repository.insert(userId, slug, normalizedOutline, normalizedMarkdown, now)
          .orElseThrow(this::revisionConflict);
    }
    if (expectedRevision < 0) {
      throw revisionConflict();
    }
    return repository.update(userId, slug, normalizedOutline, normalizedMarkdown, expectedRevision, now)
        .orElseThrow(this::revisionConflict);
  }

  public void delete(long userId, String problemSlug) {
    repository.delete(userId, requireProblem(problemSlug));
  }

  private String requireProblem(String problemSlug) {
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new ReviewException("PROBLEM_SLUG_REQUIRED", "题目 slug 不能为空。");
    }
    String normalized = problemSlug.strip();
    if (problemCatalog.findBySlug(normalized).isEmpty()) {
      throw new ReviewException("REVIEW_PROBLEM_NOT_FOUND", "未找到题目。");
    }
    return normalized;
  }

  private String normalizeMarkdown(String noteMarkdown) {
    String normalized = noteMarkdown == null ? "" : noteMarkdown;
    if (normalized.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
      throw new ReviewException("PROBLEM_NOTE_TOO_LONG", "题目笔记不能超过 10000 个字符。");
    }
    return normalized;
  }

  private ReviewException revisionConflict() {
    return new ReviewException("PROBLEM_NOTE_REVISION_CONFLICT", "题目笔记已在其他页面更新，请刷新后重试。");
  }
}
