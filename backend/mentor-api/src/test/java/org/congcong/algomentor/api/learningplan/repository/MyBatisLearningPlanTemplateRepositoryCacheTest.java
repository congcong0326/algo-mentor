package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
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
}
