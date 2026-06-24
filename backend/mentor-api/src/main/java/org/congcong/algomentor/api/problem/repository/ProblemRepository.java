package org.congcong.algomentor.api.problem.repository;

import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;

public interface ProblemRepository {

  ProblemPage<ProblemListItem> findProblems(ProblemListRequest request);

  Optional<ProblemDetail> findProblemBySlug(String slug);

  default Optional<ProblemDetail> findProblemBySlug(String slug, ProblemLocale locale) {
    return findProblemBySlug(slug);
  }

  ProblemFilters findProblemFilters();

  default ProblemFilters findProblemFilters(ProblemLocale locale) {
    return findProblemFilters();
  }

  void upsertProblem(ProblemSeedRecord problem);
}
