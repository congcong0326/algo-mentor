package org.congcong.algomentor.mentor.application.practice;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 1 唯一的 Prompt 历史提交读取边界。
 *
 * <p>仅查询用户正式 Review 的最新窄行，并通过受信题库目录补齐题名与标签。</p>
 */
public class PracticeSubmissionHistoryContextProvider {

  private final PracticeSubmissionHistoryRepository historyRepository;
  private final PracticeChatProblemCatalog problemCatalog;
  private final PracticeRelatedProblemCatalog relatedProblemCatalog;

  public PracticeSubmissionHistoryContextProvider(
      PracticeSubmissionHistoryRepository historyRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeRelatedProblemCatalog relatedProblemCatalog
  ) {
    this.historyRepository = Objects.requireNonNull(historyRepository, "history repository must not be null");
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problem catalog must not be null");
    this.relatedProblemCatalog = Objects.requireNonNull(relatedProblemCatalog, "related problem catalog must not be null");
  }

  public PracticeSubmissionHistoryContext provide(long userId, String currentProblemSlug, String locale) {
    if (userId < 1 || currentProblemSlug == null || currentProblemSlug.isBlank()) {
      return PracticeSubmissionHistoryContext.empty();
    }
    List<PracticeSubmissionHistoryProblem> submitted = historyRepository.findRecentDistinctProblems(
        userId, PracticeSubmissionHistoryContext.RECENT_SUBMITTED_PROBLEM_LIMIT);
    List<String> relatedSlugs = relatedProblemCatalog.findRelatedProblemSlugs(currentProblemSlug.trim()).stream()
        .filter(slug -> slug != null && !slug.isBlank())
        .map(String::trim)
        .filter(slug -> !currentProblemSlug.trim().equals(slug))
        .distinct()
        .toList();
    List<PracticeSubmissionHistoryProblem> related = relatedSlugs.isEmpty()
        ? List.of()
        : historyRepository.findRecentDistinctProblemsForSlugs(
            userId, relatedSlugs, PracticeSubmissionHistoryContext.RELATED_SUBMITTED_PROBLEM_LIMIT);

    Map<String, String> refsBySlug = new HashMap<>();
    return new PracticeSubmissionHistoryContext(
        entries(submitted, locale, refsBySlug),
        entries(related, locale, refsBySlug));
  }

  private List<PracticeSubmissionHistoryEntry> entries(
      List<PracticeSubmissionHistoryProblem> problems,
      String locale,
      Map<String, String> refsBySlug
  ) {
    return problems.stream()
        .map(problem -> toEntry(problem, locale, refsBySlug))
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  private java.util.Optional<PracticeSubmissionHistoryEntry> toEntry(
      PracticeSubmissionHistoryProblem problem,
      String locale,
      Map<String, String> refsBySlug
  ) {
    return problemCatalog.findProblemBySlug(problem.problemSlug(), locale)
        .map(detail -> new PracticeSubmissionHistoryEntry(
            refsBySlug.computeIfAbsent(problem.problemSlug(), ignored -> opaqueProblemRef()),
            title(detail, locale),
            detail.tags(),
            problem.reviewHistorySummary()));
  }

  private String opaqueProblemRef() {
    return "pp_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String title(PracticeChatProblemDetail detail, String locale) {
    boolean chinese = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("zh");
    if (chinese && detail.titleCn() != null && !detail.titleCn().isBlank()) {
      return detail.titleCn();
    }
    if (detail.title() != null && !detail.title().isBlank()) {
      return detail.title();
    }
    return detail.titleCn();
  }
}
