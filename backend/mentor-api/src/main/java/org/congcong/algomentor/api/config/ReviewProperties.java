package org.congcong.algomentor.api.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "algo-mentor.review")
public class ReviewProperties {

  private final CardGen cardGen = new CardGen();
  private final Scheduler scheduler = new Scheduler();
  private final Seed seed = new Seed();
  private final Queue queue = new Queue();

  public CardGen getCardGen() {
    return cardGen;
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public Seed getSeed() {
    return seed;
  }

  public Queue getQueue() {
    return queue;
  }

  public static class CardGen {
    private int dailyLimit = 20;
    private int cacheTtlHours = 168;
    private int prefetchCount = 3;

    public int getDailyLimit() {
      return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
      this.dailyLimit = dailyLimit;
    }

    public int getCacheTtlHours() {
      return cacheTtlHours;
    }

    public void setCacheTtlHours(int cacheTtlHours) {
      this.cacheTtlHours = cacheTtlHours;
    }

    public int getPrefetchCount() {
      return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
      this.prefetchCount = prefetchCount;
    }
  }

  public static class Scheduler {
    private int graduationIntervalDays = 30;
    private int graduationRepetitions = 3;
    private BigDecimal desiredRetention = new BigDecimal("0.90");
    private int maximumIntervalDays = 36500;
    private boolean enableFuzzing = true;

    public int getGraduationIntervalDays() {
      return graduationIntervalDays;
    }

    public void setGraduationIntervalDays(int graduationIntervalDays) {
      this.graduationIntervalDays = graduationIntervalDays;
    }

    public int getGraduationRepetitions() {
      return graduationRepetitions;
    }

    public void setGraduationRepetitions(int graduationRepetitions) {
      this.graduationRepetitions = graduationRepetitions;
    }

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
  }

  public static class Seed {
    private int passedFirstIntervalDays = 3;
    private int passedHighScoreIntervalDays = 4;
    private int lowConfidenceIntervalDays = 1;
    private BigDecimal highScoreRatio = new BigDecimal("0.90");

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

    public int getLowConfidenceIntervalDays() {
      return lowConfidenceIntervalDays;
    }

    public void setLowConfidenceIntervalDays(int lowConfidenceIntervalDays) {
      this.lowConfidenceIntervalDays = lowConfidenceIntervalDays;
    }

    public BigDecimal getHighScoreRatio() {
      return highScoreRatio;
    }

    public void setHighScoreRatio(BigDecimal highScoreRatio) {
      this.highScoreRatio = highScoreRatio;
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
