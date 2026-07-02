package org.congcong.algomentor.api.review.service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.api.review.model.MistakeNoteDisplayInfo;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.MistakeReviewConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class MistakeNoteDisplayInfoResolver {

  private static final Logger log = LoggerFactory.getLogger(MistakeNoteDisplayInfoResolver.class);

  private final ObjectProvider<ProblemService> problemServiceProvider;
  private final ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider;

  public MistakeNoteDisplayInfoResolver(
      ObjectProvider<ProblemService> problemServiceProvider,
      ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider
  ) {
    this.problemServiceProvider = Objects.requireNonNull(problemServiceProvider, "problemServiceProvider must not be null");
    this.practiceSessionRepositoryProvider = Objects.requireNonNull(
        practiceSessionRepositoryProvider,
        "practiceSessionRepositoryProvider must not be null");
  }

  public MistakeNoteDisplayInfo resolve(MistakeNote note, Locale fallbackLocale) {
    Objects.requireNonNull(note, "note must not be null");
    ProblemLocale problemLocale = resolveProblemLocale(note, fallbackLocale);
    Optional<ProblemDetail> problem = findProblem(note, problemLocale);
    String fallbackTitle = textFromSourceDetail(note, MistakeReviewConstants.METADATA_TITLE_CN)
        .orElse(note.problemSlug());
    String fallbackDifficulty = textFromSourceDetail(note, MistakeReviewConstants.METADATA_DIFFICULTY).orElse(null);
    return new MistakeNoteDisplayInfo(
        problem.map(ProblemDetail::title).filter(title -> !title.isBlank()).orElse(fallbackTitle),
        problemLocale.value(),
        problem.map(detail -> detail.difficulty() == null ? null : detail.difficulty().name()).orElse(fallbackDifficulty));
  }

  private ProblemLocale resolveProblemLocale(MistakeNote note, Locale fallbackLocale) {
    Optional<String> sessionLocale = note.originPracticeSessionId() == null
        ? Optional.empty()
        : findPracticeSessionLocale(note);
    return sessionLocale.map(this::parseProblemLocale)
        .orElseGet(() -> parseProblemLocale(ApiErrorLocales.resolve(fallbackLocale).toLanguageTag()));
  }

  private Optional<String> findPracticeSessionLocale(MistakeNote note) {
    PracticeSessionRepository repository = practiceSessionRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return Optional.empty();
    }
    try {
      return repository.findSessionForUser(note.originPracticeSessionId(), note.userId())
          .map(session -> session.locale())
          .filter(locale -> locale != null && !locale.isBlank());
    } catch (RuntimeException exception) {
      log.warn("Practice session lookup failed for mistake note display. noteId={} sessionId={} exceptionType={}",
          note.id(),
          note.originPracticeSessionId(),
          exception.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private Optional<ProblemDetail> findProblem(MistakeNote note, ProblemLocale problemLocale) {
    ProblemService problemService = problemServiceProvider.getIfAvailable();
    if (problemService == null) {
      return Optional.empty();
    }
    try {
      return problemService.findProblemBySlug(note.problemSlug(), problemLocale);
    } catch (RuntimeException exception) {
      log.warn("Problem lookup failed for mistake note display. noteId={} problemSlug={} locale={} exceptionType={}",
          note.id(),
          note.problemSlug(),
          problemLocale.value(),
          exception.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private ProblemLocale parseProblemLocale(String locale) {
    try {
      return ProblemLocale.parse(locale);
    } catch (RuntimeException exception) {
      return ProblemLocale.DEFAULT;
    }
  }

  private Optional<String> textFromSourceDetail(MistakeNote note, String key) {
    Object value = note.sourceDetail().get(key);
    if (value == null) {
      return Optional.empty();
    }
    String text = value.toString().trim();
    return text.isBlank() ? Optional.empty() : Optional.of(text);
  }
}
