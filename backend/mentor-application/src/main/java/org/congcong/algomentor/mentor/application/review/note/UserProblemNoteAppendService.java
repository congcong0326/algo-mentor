package org.congcong.algomentor.mentor.application.review.note;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;

/** 对已经由上游校验为当前用户当前题目的笔记执行原子追加。 */
public final class UserProblemNoteAppendService {

  private final UserProblemNoteRepository repository;
  private final Clock clock;

  public UserProblemNoteAppendService(UserProblemNoteRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public UserProblemNote appendToTrustedProblem(long userId, String problemSlug, String contentMarkdown) {
    if (userId < 1 || problemSlug == null || problemSlug.isBlank()) {
      throw new ReviewException("PROBLEM_NOTE_TRUSTED_CONTEXT_REQUIRED", "当前题目笔记上下文不可用。");
    }
    String normalizedContent = normalizeContent(contentMarkdown);
    return repository.append(
            userId,
            problemSlug.strip(),
            ProblemSolutionOutlineV1.empty(),
            normalizedContent,
            Instant.now(clock))
        .orElseThrow(() -> new ReviewException(
            "PROBLEM_NOTE_TOO_LONG", "追加后题目笔记不能超过 10000 个字符。"));
  }

  private String normalizeContent(String contentMarkdown) {
    String normalized = contentMarkdown == null ? "" : contentMarkdown.strip();
    if (normalized.isEmpty()) {
      throw new ReviewException("PROBLEM_NOTE_APPEND_CONTENT_REQUIRED", "追加的题目笔记不能为空。");
    }
    if (normalized.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
      throw new ReviewException("PROBLEM_NOTE_TOO_LONG", "题目笔记不能超过 10000 个字符。");
    }
    return normalized;
  }
}
