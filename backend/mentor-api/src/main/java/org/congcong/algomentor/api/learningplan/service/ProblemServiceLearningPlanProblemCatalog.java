package org.congcong.algomentor.api.learningplan.service;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
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
    return problemService.findProblemBySlug(slug)
        .map(problem -> new LearningPlanProblemCandidate(
            problem.slug(),
            problem.frontendId(),
            problem.title(),
            null,
            problem.difficulty() == null ? null : problem.difficulty().name(),
            tagLabels(problem.tags())));
  }

  private LearningPlanProblemCandidate toCandidate(ProblemListItem problem) {
    return new LearningPlanProblemCandidate(
        problem.slug(),
        problem.frontendId(),
        problem.title(),
        null,
        problem.difficulty() == null ? null : problem.difficulty().name(),
        tagLabels(problem.tags()));
  }

  private List<String> tagLabels(List<ProblemTag> tags) {
    return tags.stream()
        .map(ProblemTag::label)
        .toList();
  }
}
