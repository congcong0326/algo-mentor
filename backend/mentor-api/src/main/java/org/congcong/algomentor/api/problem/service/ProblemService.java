package org.congcong.algomentor.api.problem.service;

import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ProblemService {

  private final ObjectProvider<ProblemRepository> repositoryProvider;

  public ProblemService(ObjectProvider<ProblemRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
    return repository().findProblems(request);
  }

  public Optional<ProblemDetail> findProblemBySlug(String slug) {
    return repository().findProblemBySlug(slug);
  }

  public Optional<ProblemDetail> findProblemBySlug(String slug, ProblemLocale locale) {
    return repository().findProblemBySlug(slug, locale);
  }

  public ProblemFilters findProblemFilters() {
    return repository().findProblemFilters();
  }

  public ProblemFilters findProblemFilters(ProblemLocale locale) {
    return repository().findProblemFilters(locale);
  }

  private ProblemRepository repository() {
    ProblemRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemRepositoryUnavailableException();
    }
    return repository;
  }

  public static class ProblemRepositoryUnavailableException extends RuntimeException {
    public ProblemRepositoryUnavailableException() {
      super("Problem repository is unavailable. Enable the local datasource profile before using problem APIs.");
    }
  }
}
