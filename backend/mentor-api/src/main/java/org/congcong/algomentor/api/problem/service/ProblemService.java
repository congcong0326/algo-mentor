package org.congcong.algomentor.api.problem.service;

import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemStaticSnapshot;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemStaticSnapshotRepository;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProblemService {

  private final ObjectProvider<ProblemRepository> repositoryProvider;
  private final ProblemStaticCache cache;

  public ProblemService(ObjectProvider<ProblemRepository> repositoryProvider) {
    this(repositoryProvider, null, null);
  }

  @Autowired
  public ProblemService(
      ObjectProvider<ProblemRepository> repositoryProvider,
      ObjectProvider<LocalCacheRegionFactory> cacheFactoryProvider,
      ObjectProvider<ProblemCacheProperties> cachePropertiesProvider
  ) {
    this.repositoryProvider = repositoryProvider;
    LocalCacheRegionFactory cacheFactory = cacheFactoryProvider == null ? null : cacheFactoryProvider.getIfAvailable();
    ProblemCacheProperties cacheProperties = cachePropertiesProvider == null
        ? null
        : cachePropertiesProvider.getIfAvailable();
    cache = cacheFactory == null || cacheProperties == null ? null : new ProblemStaticCache(cacheFactory, cacheProperties);
  }

  public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
    return repository().findProblems(request);
  }

  public Optional<ProblemDetail> findProblemBySlug(String slug) {
    return findProblemBySlug(slug, ProblemLocale.DEFAULT);
  }

  public Optional<ProblemDetail> findProblemBySlug(String slug, ProblemLocale locale) {
    ProblemRepository repository = repository();
    if (cache == null || !(repository instanceof ProblemStaticSnapshotRepository snapshots)) {
      return repository.findProblemBySlug(slug, locale);
    }
    return cache.getSnapshot(slug, () -> snapshots.findStaticSnapshotBySlug(ProblemStaticCache.normalizeSlug(slug)))
        .map(snapshot -> toDetail(snapshot, locale));
  }

  public ProblemFilters findProblemFilters() {
    return findProblemFilters(ProblemLocale.DEFAULT);
  }

  public ProblemFilters findProblemFilters(ProblemLocale locale) {
    ProblemRepository repository = repository();
    if (cache == null) {
      return repository.findProblemFilters(locale);
    }
    return cache.getFilters(locale, () -> repository.findProblemFilters(locale));
  }

  private ProblemRepository repository() {
    ProblemRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemRepositoryUnavailableException();
    }
    return repository;
  }

  private ProblemDetail toDetail(ProblemStaticSnapshot snapshot, ProblemLocale locale) {
    return new ProblemDetail(
        snapshot.slug(),
        snapshot.frontendId(),
        snapshot.frontendDisplayId(),
        localized(locale, snapshot.titleEn(), snapshot.titleZh()),
        snapshot.difficulty(),
        snapshot.tags().stream()
            .map(tag -> new ProblemTag(tag.value(), localized(locale, tag.labelEn(), tag.labelZh())))
            .toList(),
        localized(locale, snapshot.contentMarkdownEn(), snapshot.contentMarkdownZh()),
        snapshot.contentStatus(),
        snapshot.leetcodeUrl(),
        snapshot.sampleTestCase(),
        snapshot.python3Template(),
        snapshot.sourceCommit(),
        localized(locale, snapshot.recommendationReasonEn(), snapshot.recommendationReasonZh()));
  }

  private String localized(ProblemLocale locale, String english, String chinese) {
    return locale.isEnglish() ? fallback(english, chinese) : fallback(chinese, english);
  }

  private String fallback(String value, String alternative) {
    return value == null || value.isBlank() ? alternative : value;
  }

  public static class ProblemRepositoryUnavailableException extends RuntimeException {
    public ProblemRepositoryUnavailableException() {
      super("Problem repository is unavailable. Enable the local datasource profile before using problem APIs.");
    }
  }
}
