package org.congcong.algomentor.api.learningplan.config;

import java.time.Duration;
import java.time.ZoneId;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 学习计划配额日期边界和过期数据清理的节点级运维参数。 */
@ConfigurationProperties(prefix = MentorConfigurationKeys.LEARNING_PLAN_GOVERNANCE_PREFIX)
public class LearningPlanGovernanceProperties {

  private String quotaZone = "UTC";
  private Cleanup cleanup = new Cleanup();
  private GenerationRecovery generationRecovery = new GenerationRecovery();

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

  public GenerationRecovery getGenerationRecovery() {
    return generationRecovery;
  }

  public void setGenerationRecovery(GenerationRecovery generationRecovery) {
    this.generationRecovery = generationRecovery == null ? new GenerationRecovery() : generationRecovery;
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

  /** 进程重启后收敛无法续跑的首次草案生成。 */
  public static class GenerationRecovery {

    private boolean enabled = true;
    private Duration minimumAge = Duration.ofMinutes(1);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getMinimumAge() {
      return minimumAge;
    }

    public void setMinimumAge(Duration minimumAge) {
      if (minimumAge == null || minimumAge.isNegative()) {
        throw new IllegalArgumentException("learning plan generation recovery minimum age must not be negative");
      }
      this.minimumAge = minimumAge;
    }
  }
}
