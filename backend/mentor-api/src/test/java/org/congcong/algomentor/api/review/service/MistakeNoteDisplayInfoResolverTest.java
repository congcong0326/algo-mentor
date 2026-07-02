package org.congcong.algomentor.api.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.congcong.algomentor.mentor.application.review.MasteryState;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.MistakeReviewConstants;
import org.congcong.algomentor.mentor.application.review.MistakeSource;
import org.congcong.algomentor.mentor.application.review.SchedulingState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MistakeNoteDisplayInfoResolverTest {

  private final ProblemService problemService = mock(ProblemService.class);
  private final PracticeSessionRepository practiceSessionRepository = mock(PracticeSessionRepository.class);
  private final MistakeNoteDisplayInfoResolver resolver = new MistakeNoteDisplayInfoResolver(
      provider(problemService),
      provider(practiceSessionRepository));

  @Test
  void practiceSessionLocaleTakesPrecedenceOverRequestLocale() {
    MistakeNote note = note(1, "two-sum", 900L, Map.of());
    when(practiceSessionRepository.findSessionForUser(900L, 7L))
        .thenReturn(Optional.of(session(900L, "two-sum", "en-US")));
    when(problemService.findProblemBySlug("two-sum", ProblemLocale.EN_US))
        .thenReturn(Optional.of(problem("two-sum", "Two Sum", ProblemDifficulty.EASY)));

    var display = resolver.resolve(note, Locale.forLanguageTag("zh-CN"));

    assertThat(display.problemTitle()).isEqualTo("Two Sum");
    assertThat(display.problemLocale()).isEqualTo("en-US");
    assertThat(display.problemDifficulty()).isEqualTo("EASY");
    verify(problemService).findProblemBySlug("two-sum", ProblemLocale.EN_US);
  }

  @Test
  void requestLocaleIsUsedWhenNoteHasNoPracticeSession() {
    MistakeNote note = note(2, "merge-sorted-array", null, Map.of());
    when(problemService.findProblemBySlug("merge-sorted-array", ProblemLocale.EN_US))
        .thenReturn(Optional.of(problem("merge-sorted-array", "Merge Sorted Array", ProblemDifficulty.EASY)));

    var display = resolver.resolve(note, Locale.forLanguageTag("en-US"));

    assertThat(display.problemTitle()).isEqualTo("Merge Sorted Array");
    assertThat(display.problemLocale()).isEqualTo("en-US");
    assertThat(display.problemDifficulty()).isEqualTo("EASY");
  }

  @Test
  void sourceDetailAndSlugAreUsedWhenProblemCatalogMisses() {
    MistakeNote withSourceTitle = note(3, "missing-slug", null, Map.of(
        MistakeReviewConstants.METADATA_TITLE_CN, "历史题名",
        MistakeReviewConstants.METADATA_DIFFICULTY, "MEDIUM"));
    when(problemService.findProblemBySlug("missing-slug", ProblemLocale.ZH_CN)).thenReturn(Optional.empty());

    var display = resolver.resolve(withSourceTitle, Locale.forLanguageTag("zh-CN"));

    assertThat(display.problemTitle()).isEqualTo("历史题名");
    assertThat(display.problemLocale()).isEqualTo("zh-CN");
    assertThat(display.problemDifficulty()).isEqualTo("MEDIUM");

    MistakeNote withoutSourceTitle = note(4, "slug-only", null, Map.of());
    when(problemService.findProblemBySlug("slug-only", ProblemLocale.ZH_CN)).thenReturn(Optional.empty());

    var slugDisplay = resolver.resolve(withoutSourceTitle, Locale.forLanguageTag("zh-CN"));

    assertThat(slugDisplay.problemTitle()).isEqualTo("slug-only");
    assertThat(slugDisplay.problemDifficulty()).isNull();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private static MistakeNote note(
      long id,
      String problemSlug,
      Long originPracticeSessionId,
      Map<String, Object> sourceDetail
  ) {
    Instant now = Instant.parse("2026-07-02T00:00:00Z");
    return new MistakeNote(
        id,
        7L,
        problemSlug,
        MistakeSource.REVIEW_FAILED,
        sourceDetail,
        100L,
        1,
        originPracticeSessionId,
        new SchedulingState(0, new BigDecimal("2.50"), 0, MasteryState.NEW, 0),
        now,
        null,
        null,
        false,
        "",
        null,
        now,
        now);
  }

  private static PracticeSession session(long id, String problemSlug, String locale) {
    Instant now = Instant.parse("2026-07-02T00:00:00Z");
    return new PracticeSession(
        id,
        7L,
        100L,
        1,
        problemSlug,
        PracticeSessionStatus.ACTIVE,
        null,
        null,
        PracticeProgressStatus.IN_PROGRESS,
        now,
        now,
        now,
        locale);
  }

  private static ProblemDetail problem(String slug, String title, ProblemDifficulty difficulty) {
    return new ProblemDetail(slug, 1, title, difficulty, List.of(), "", "", "", "", "");
  }
}
