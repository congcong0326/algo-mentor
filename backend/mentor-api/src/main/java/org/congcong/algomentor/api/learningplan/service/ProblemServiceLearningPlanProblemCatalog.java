package org.congcong.algomentor.api.learningplan.service;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.model.ProblemSort;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.springframework.stereotype.Component;

@Component
public class ProblemServiceLearningPlanProblemCatalog implements LearningPlanProblemCatalog {

  private final ProblemService problemService;

  public ProblemServiceLearningPlanProblemCatalog(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
    return problemService.findProblems(new ProblemListRequest(
            search.keyword(),
            null,
            null,
            null,
            null,
            null,
            null,
            ProblemSort.FRONTEND_ID_ASC,
            1,
            search.limit(),
            null))
        .items()
        .stream()
        .map(this::toCandidate)
        .toList();
  }

  @Override
  public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
    return findBySlug(slug, ProblemLocale.DEFAULT.value());
  }

  @Override
  public Optional<LearningPlanProblemCandidate> findBySlug(String slug, String locale) {
    ProblemLocale requestedLocale = ProblemLocale.parse(locale);
    Optional<ProblemDetail> requested = problemService.findProblemBySlug(slug, requestedLocale);
    if (requested.isEmpty()) {
      return Optional.empty();
    }
    Optional<ProblemDetail> english = requestedLocale == ProblemLocale.EN_US
        ? requested
        : problemService.findProblemBySlug(slug, ProblemLocale.EN_US);
    Optional<ProblemDetail> chinese = requestedLocale == ProblemLocale.ZH_CN
        ? requested
        : problemService.findProblemBySlug(slug, ProblemLocale.ZH_CN);
    return Optional.of(toCandidate(requested.orElseThrow(), english.orElse(null), chinese.orElse(null)));
  }

  private LearningPlanProblemCandidate toCandidate(ProblemListItem problem) {
    return new LearningPlanProblemCandidate(
        problem.slug(),
        problem.frontendId(),
        problem.title(),
        null,
        problem.difficulty() == null ? null : problem.difficulty().name(),
        tagValues(problem.tags()));
  }

  private LearningPlanProblemCandidate toCandidate(
      ProblemDetail requested,
      ProblemDetail english,
      ProblemDetail chinese
  ) {
    return new LearningPlanProblemCandidate(
        requested.slug(),
        requested.frontendId(),
        english == null ? requested.title() : english.title(),
        chinese == null ? null : chinese.title(),
        requested.difficulty() == null ? null : requested.difficulty().name(),
        tagValues(requested.tags()),
        requested.recommendationReason());
  }

  @Override
  public Optional<String> findCanonicalTagValue(String tag, String locale) {
    if (tag == null || tag.isBlank()) {
      return Optional.empty();
    }
    String candidate = tag.trim();
    return problemService.findProblemFilters(ProblemLocale.parse(locale)).tags().stream()
        .filter(option -> candidate.equals(option.value()) || candidate.equals(option.label()))
        .map(ProblemFilterOption::value)
        .findFirst();
  }

  private List<String> tagValues(List<ProblemTag> tags) {
    return tags.stream()
        .map(ProblemTag::value)
        .toList();
  }
}
