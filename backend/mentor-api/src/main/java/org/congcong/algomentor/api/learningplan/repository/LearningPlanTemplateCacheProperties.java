package org.congcong.algomentor.api.learningplan.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 发布期不可变学习计划模板目录缓存的容量配置。 */
@ConfigurationProperties(prefix = "algo-mentor.learning-plan-template.cache")
public class LearningPlanTemplateCacheProperties {

  private long catalogMaximumSize = 1;

  public long getCatalogMaximumSize() {
    return catalogMaximumSize;
  }

  public void setCatalogMaximumSize(long catalogMaximumSize) {
    if (catalogMaximumSize < 1) {
      throw new IllegalArgumentException("catalogMaximumSize must be positive");
    }
    this.catalogMaximumSize = catalogMaximumSize;
  }
}
