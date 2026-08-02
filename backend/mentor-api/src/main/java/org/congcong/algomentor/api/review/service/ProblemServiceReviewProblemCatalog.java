package org.congcong.algomentor.api.review.service;

import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProblemServiceReviewProblemCatalog implements ReviewProblemCatalog {

  private static final Logger log = LoggerFactory.getLogger(ProblemServiceReviewProblemCatalog.class);

  private final ProblemService problemService;

  public ProblemServiceReviewProblemCatalog(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public Optional<ReviewProblemSnapshot> findBySlug(String slug) {
    return findBySlug(slug, ProblemLocale.DEFAULT.value());
  }

  @Override
  public Optional<ReviewProblemSnapshot> findBySlug(String slug, String locale) {
    try {
      return problemService.findProblemBySlug(slug, ProblemLocale.parse(locale))
          .map(problem -> new ReviewProblemSnapshot(
              problem.slug(),
              problem.title(),
              problem.difficulty() == null ? null : problem.difficulty().name(),
              ProblemStatementExtractor.summary(problem.contentMarkdown()),
              problem.contentMarkdown()));
    } catch (RuntimeException exception) {
      log.warn("Review problem lookup failed. problemSlug={} exceptionType={}",
          slug,
          exception.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
