package org.congcong.algomentor.api.admin.overview;

import io.micrometer.core.instrument.MeterRegistry;

public class AdminOverviewMetrics {
  private final MeterRegistry registry;
  public AdminOverviewMetrics(MeterRegistry registry) { this.registry = registry; }
  public void sectionFailure(String section) {
    if (registry != null) registry.counter("admin.overview.section.failure", "section", section).increment();
  }
}
