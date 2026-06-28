package org.congcong.algomentor.api.practice.service;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemServicePracticeChatProblemCatalog implements PracticeChatProblemCatalog {

  private final ProblemService problemService;

  public ProblemServicePracticeChatProblemCatalog(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public Optional<PracticeChatProblemDetail> findProblemBySlug(String slug, String locale) {
    return problemService.findProblemBySlug(slug, ProblemLocale.parse(locale))
        .map(problem -> new PracticeChatProblemDetail(
            problem.slug(),
            problem.frontendId(),
            problem.title(),
            problemService.findProblemBySlug(slug, ProblemLocale.ZH_CN)
                .map(zhProblem -> zhProblem.title())
                .orElse(problem.title()),
            problem.difficulty() == null ? null : problem.difficulty().name(),
            tagLabels(problem.tags()),
            problem.contentMarkdown(),
            problem.leetcodeUrl()));
  }

  private List<String> tagLabels(List<ProblemTag> tags) {
    return tags.stream()
        .map(ProblemTag::label)
        .toList();
  }
}
