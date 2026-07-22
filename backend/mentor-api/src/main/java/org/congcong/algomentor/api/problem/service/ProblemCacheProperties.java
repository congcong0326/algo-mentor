package org.congcong.algomentor.api.problem.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 题库发布期不可变快照缓存的容量配置。 */
@ConfigurationProperties(prefix = "algo-mentor.problem.cache")
public class ProblemCacheProperties {

  private long staticSnapshotMaximumSize = 4_000;
  private long filtersMaximumSize = 2;

  public long getStaticSnapshotMaximumSize() {
    return staticSnapshotMaximumSize;
  }

  public void setStaticSnapshotMaximumSize(long staticSnapshotMaximumSize) {
    this.staticSnapshotMaximumSize = requirePositive(staticSnapshotMaximumSize, "staticSnapshotMaximumSize");
  }

  public long getFiltersMaximumSize() {
    return filtersMaximumSize;
  }

  public void setFiltersMaximumSize(long filtersMaximumSize) {
    this.filtersMaximumSize = requirePositive(filtersMaximumSize, "filtersMaximumSize");
  }

  private static long requirePositive(long value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
