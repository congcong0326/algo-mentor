package org.congcong.algomentor.api.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "algo-mentor.review")
public class ReviewProperties {

  private final Scheduler scheduler = new Scheduler();
  private final Seed seed = new Seed();
  private final Queue queue = new Queue();

  public Scheduler getScheduler() {
    return scheduler;
  }

  public Seed getSeed() {
    return seed;
  }

  public Queue getQueue() {
    return queue;
  }

  public static class Scheduler {
    private BigDecimal desiredRetention = new BigDecimal("0.90");
    private int maximumIntervalDays = 36500;
    private boolean enableFuzzing = true;
    private int graduatingIntervalDays = 1;
    private int easyIntervalDays = 4;

    public BigDecimal getDesiredRetention() {
      return desiredRetention;
    }

    public void setDesiredRetention(BigDecimal desiredRetention) {
      this.desiredRetention = desiredRetention;
    }

    public int getMaximumIntervalDays() {
      return maximumIntervalDays;
    }

    public void setMaximumIntervalDays(int maximumIntervalDays) {
      this.maximumIntervalDays = maximumIntervalDays;
    }

    public boolean isEnableFuzzing() {
      return enableFuzzing;
    }

    public void setEnableFuzzing(boolean enableFuzzing) {
      this.enableFuzzing = enableFuzzing;
    }

    public int getGraduatingIntervalDays() {
      return graduatingIntervalDays;
    }

    public void setGraduatingIntervalDays(int graduatingIntervalDays) {
      this.graduatingIntervalDays = graduatingIntervalDays;
    }

    public int getEasyIntervalDays() {
      return easyIntervalDays;
    }

    public void setEasyIntervalDays(int easyIntervalDays) {
      this.easyIntervalDays = easyIntervalDays;
    }
  }

  public static class Seed {
    private BigDecimal highScoreRatio = new BigDecimal("0.90");
    private int lowConfidenceFirstIntervalDays = 1;
    private int passedFirstIntervalDays = 3;
    private int passedHighScoreIntervalDays = 4;

    public BigDecimal getHighScoreRatio() {
      return highScoreRatio;
    }

    public void setHighScoreRatio(BigDecimal highScoreRatio) {
      this.highScoreRatio = highScoreRatio;
    }

    public int getLowConfidenceFirstIntervalDays() {
      return lowConfidenceFirstIntervalDays;
    }

    public void setLowConfidenceFirstIntervalDays(int lowConfidenceFirstIntervalDays) {
      this.lowConfidenceFirstIntervalDays = lowConfidenceFirstIntervalDays;
    }

    public int getPassedFirstIntervalDays() {
      return passedFirstIntervalDays;
    }

    public void setPassedFirstIntervalDays(int passedFirstIntervalDays) {
      this.passedFirstIntervalDays = passedFirstIntervalDays;
    }

    public int getPassedHighScoreIntervalDays() {
      return passedHighScoreIntervalDays;
    }

    public void setPassedHighScoreIntervalDays(int passedHighScoreIntervalDays) {
      this.passedHighScoreIntervalDays = passedHighScoreIntervalDays;
    }
  }

  public static class Queue {
    private int dailyCap = 20;
    private int dailyNewLimit = 10;
    private int dailyLearningLimit = 50;
    private int dailyReviewLimit = 30;

    public int getDailyCap() {
      return dailyCap;
    }

    public void setDailyCap(int dailyCap) {
      this.dailyCap = dailyCap;
    }

    public int getDailyNewLimit() {
      return dailyNewLimit;
    }

    public void setDailyNewLimit(int dailyNewLimit) {
      this.dailyNewLimit = dailyNewLimit;
    }

    public int getDailyLearningLimit() {
      return dailyLearningLimit;
    }

    public void setDailyLearningLimit(int dailyLearningLimit) {
      this.dailyLearningLimit = dailyLearningLimit;
    }

    public int getDailyReviewLimit() {
      return dailyReviewLimit;
    }

    public void setDailyReviewLimit(int dailyReviewLimit) {
      this.dailyReviewLimit = dailyReviewLimit;
    }
  }
}
