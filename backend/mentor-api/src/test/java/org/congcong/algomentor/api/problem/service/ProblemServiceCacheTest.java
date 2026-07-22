package org.congcong.algomentor.api.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemStaticSnapshot;
import org.congcong.algomentor.api.problem.model.TrustedProblemTag;
import org.congcong.algomentor.api.problem.model.NormalizedProblemSeed;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.repository.ProblemStaticSnapshotRepository;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProblemServiceCacheTest {

  @Test
  void sharesOneBilingualSnapshotAcrossLocalesAndCachesFiltersByLocale() {
    StaticRepository repository = new StaticRepository();
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    beans.addBean("problemRepository", repository);
    beans.addBean("cacheFactory", new CaffeineLocalCacheRegionFactory(
        new CacheRegionRegistry(), new NoopCacheMetrics()));
    beans.addBean("cacheProperties", new ProblemCacheProperties());
    ProblemService service = new ProblemService(
        beans.getBeanProvider(ProblemRepository.class),
        beans.getBeanProvider(LocalCacheRegionFactory.class),
        beans.getBeanProvider(ProblemCacheProperties.class));

    ProblemDetail chinese = service.findProblemBySlug("Two-Sum", ProblemLocale.ZH_CN).orElseThrow();
    ProblemDetail english = service.findProblemBySlug("two-sum", ProblemLocale.EN_US).orElseThrow();
    service.findProblemFilters(ProblemLocale.ZH_CN);
    service.findProblemFilters(ProblemLocale.ZH_CN);
    service.findProblemFilters(ProblemLocale.EN_US);

    assertThat(chinese.title()).isEqualTo("两数之和");
    assertThat(english.title()).isEqualTo("Two Sum");
    assertThat(repository.snapshotLoads).hasValue(1);
    assertThat(repository.filterLoads).hasValue(2);
  }

  private static final class StaticRepository implements ProblemRepository, ProblemStaticSnapshotRepository {
    private final AtomicInteger snapshotLoads = new AtomicInteger();
    private final AtomicInteger filterLoads = new AtomicInteger();

    @Override
    public Optional<ProblemStaticSnapshot> findStaticSnapshotBySlug(String slug) {
      snapshotLoads.incrementAndGet();
      return Optional.of(new ProblemStaticSnapshot(
          "two-sum", 1, "1", "Two Sum", "两数之和", ProblemDifficulty.EASY,
          List.of(new TrustedProblemTag(1, "array", "Array", "数组")),
          "English statement", "中文题面", "BILINGUAL", "https://leetcode.com/problems/two-sum",
          "[2,7,11,15]", "class Solution: ...", "seed", "English reason", "中文理由"));
    }

    @Override
    public ProblemPage<ProblemListItem> findProblems(ProblemListRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<ProblemDetail> findProblemBySlug(String slug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ProblemFilters findProblemFilters() {
      filterLoads.incrementAndGet();
      return new ProblemFilters(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Override
    public void upsertProblem(NormalizedProblemSeed problem) {
      throw new UnsupportedOperationException();
    }
  }
}
