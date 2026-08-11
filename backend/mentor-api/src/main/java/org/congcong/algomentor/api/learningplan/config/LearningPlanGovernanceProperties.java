package org.congcong.algomentor.api.learningplan.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 学习计划配额日期边界和过期数据清理的节点级运维参数。 */
@ConfigurationProperties(prefix = "algo-mentor.learning-plan.governance")
public class LearningPlanGovernanceProperties {

  private String quotaZone = "UTC";
  private Cleanup cleanup = new Cleanup();

  public String getQuotaZone() {
    return quotaZone;
  }

  public void setQuotaZone(String quotaZone) {
    ZoneId.of(quotaZone);
    this.quotaZone = quotaZone;
  }

  public ZoneId quotaZoneId() {
    return ZoneId.of(quotaZone);
  }

  public Cleanup getCleanup() {
    return cleanup;
  }

  public void setCleanup(Cleanup cleanup) {
    this.cleanup = cleanup == null ? new Cleanup() : cleanup;
  }

  public static class Cleanup {

    private boolean enabled = true;
    private Duration fixedDelay = Duration.ofHours(1);
    private int batchSize = 1_000;
    private int dailyUsageRetentionDays = 30;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getFixedDelay() {
      return fixedDelay;
    }

    public void setFixedDelay(Duration fixedDelay) {
      if (fixedDelay == null || fixedDelay.compareTo(Duration.ofMillis(1)) < 0) {
        throw new IllegalArgumentException(
            "learning plan cleanup fixed delay must be at least 1 millisecond");
      }
      this.fixedDelay = fixedDelay;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      if (batchSize < 1 || batchSize > 100_000) {
        throw new IllegalArgumentException("learning plan cleanup batch size must be between 1 and 100000");
      }
      this.batchSize = batchSize;
    }

    public int getDailyUsageRetentionDays() {
      return dailyUsageRetentionDays;
    }

    public void setDailyUsageRetentionDays(int dailyUsageRetentionDays) {
      if (dailyUsageRetentionDays < 1 || dailyUsageRetentionDays > 3_650) {
        throw new IllegalArgumentException(
            "learning plan daily usage retention days must be between 1 and 3650");
      }
      this.dailyUsageRetentionDays = dailyUsageRetentionDays;
    }
  }
}
