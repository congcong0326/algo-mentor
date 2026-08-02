package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.junit.jupiter.api.Test;

class MyBatisLearningPlanTemplateRepositoryCacheTest {

  @Test
  void reusesOneCatalogLoadedByThreeBatchQueries() {
    LearningPlanTemplateMapper mapper = mock(LearningPlanTemplateMapper.class);
    when(mapper.findAllTemplates()).thenReturn(List.of());
    when(mapper.findAllPhases()).thenReturn(List.of());
    when(mapper.findAllProblemRefs()).thenReturn(List.of());
    MyBatisLearningPlanTemplateRepository repository = new MyBatisLearningPlanTemplateRepository(
        mapper,
        new ObjectMapper(),
        new CaffeineLocalCacheRegionFactory(new CacheRegionRegistry(), new NoopCacheMetrics()),
        new LearningPlanTemplateCacheProperties());

    assertThat(repository.findAllTemplates()).isEmpty();
    assertThat(repository.findByTemplateId("missing")).isEmpty();

    verify(mapper).findAllTemplates();
    verify(mapper).findAllPhases();
    verify(mapper).findAllProblemRefs();
    verify(mapper, never()).findByTemplateId("missing");
  }

  @Test
  void isolatesCachedCatalogsByContentLocale() {
    LearningPlanTemplateMapper mapper = emptyCatalogMapper();
    MyBatisLearningPlanTemplateRepository repository = repository(mapper, Runnable::run);

    assertThat(repository.findAllTemplates(LearningPlanContentLocale.ZH_CN)).isEmpty();
    assertThat(repository.findAllTemplates(LearningPlanContentLocale.EN_US)).isEmpty();
    assertThat(repository.findByTemplateId("missing", LearningPlanContentLocale.ZH_CN)).isEmpty();
    assertThat(repository.findByTemplateId("missing", LearningPlanContentLocale.EN_US)).isEmpty();

    verify(mapper, times(2)).findAllTemplates();
    verify(mapper, times(2)).findAllPhases();
    verify(mapper, times(2)).findAllProblemRefs();
    verify(mapper, never()).findByTemplateId("missing");
  }

  @Test
  void invalidatesEveryLocaleOnlyAfterTheCommitCallbackRuns() {
    LearningPlanTemplateMapper mapper = emptyCatalogMapper();
    RecordingInvalidationExecutor invalidationExecutor = new RecordingInvalidationExecutor();
    MyBatisLearningPlanTemplateRepository repository = repository(mapper, invalidationExecutor);
    repository.findAllTemplates(LearningPlanContentLocale.ZH_CN);
    repository.findAllTemplates(LearningPlanContentLocale.EN_US);

    repository.scheduleCatalogCacheInvalidation();

    repository.findAllTemplates(LearningPlanContentLocale.ZH_CN);
    verify(mapper, times(2)).findAllTemplates();
    invalidationExecutor.runAfterCommit();
    repository.findAllTemplates(LearningPlanContentLocale.ZH_CN);
    repository.findAllTemplates(LearningPlanContentLocale.EN_US);
    verify(mapper, times(4)).findAllTemplates();
  }

  private LearningPlanTemplateMapper emptyCatalogMapper() {
    LearningPlanTemplateMapper mapper = mock(LearningPlanTemplateMapper.class);
    when(mapper.findAllTemplates()).thenReturn(List.of());
    when(mapper.findAllPhases()).thenReturn(List.of());
    when(mapper.findAllProblemRefs()).thenReturn(List.of());
    return mapper;
  }

  private MyBatisLearningPlanTemplateRepository repository(
      LearningPlanTemplateMapper mapper,
      org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor invalidationExecutor
  ) {
    return new MyBatisLearningPlanTemplateRepository(
        mapper,
        new ObjectMapper(),
        new CaffeineLocalCacheRegionFactory(new CacheRegionRegistry(), new NoopCacheMetrics()),
        new LearningPlanTemplateCacheProperties(),
        invalidationExecutor);
  }

  private static final class RecordingInvalidationExecutor
      implements org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor {

    private Runnable callback;

    @Override
    public void afterCommit(Runnable invalidation) {
      callback = invalidation;
    }

    void runAfterCommit() {
      callback.run();
    }
  }
}
