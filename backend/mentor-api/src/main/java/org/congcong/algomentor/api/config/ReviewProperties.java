package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "algo-mentor.review")
public class ReviewProperties {

  private final CardGen cardGen = new CardGen();
  private final Scheduler scheduler = new Scheduler();

  public CardGen getCardGen() {
    return cardGen;
  }

  public Scheduler getScheduler() {
    return scheduler;
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
  }
}
