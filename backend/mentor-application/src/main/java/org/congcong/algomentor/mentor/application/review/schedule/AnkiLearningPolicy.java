package org.congcong.algomentor.mentor.application.review.schedule;

import java.time.Duration;
import java.util.Objects;

/** 两步学习阶段的固定 Anki 式评分语义。 */
public class AnkiLearningPolicy {

  private final Duration firstStep;
  private final Duration secondStep;
  private final Duration firstStepHardDelay;
  private final int graduatingIntervalDays;
  private final int easyIntervalDays;

  public AnkiLearningPolicy(ReviewSchedulerProperties properties) {
    Objects.requireNonNull(properties, "properties must not be null");
    Duration[] steps = properties.learningSteps();
    this.firstStep = steps[0];
    this.secondStep = steps[1];
    this.firstStepHardDelay = firstStep.plus(secondStep).dividedBy(2);
    this.graduatingIntervalDays = properties.graduatingIntervalDays();
    this.easyIntervalDays = properties.easyIntervalDays();
  }

  public Decision schedule(SchedulingState state, ReviewRating rating) {
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    return currentStep(state) == 0 ? firstStepDecision(rating) : secondStepDecision(rating);
  }

  private Decision firstStepDecision(ReviewRating rating) {
    return switch (rating) {
      case AGAIN -> Decision.learning(0, firstStep);
      case HARD -> Decision.learning(0, firstStepHardDelay);
      case GOOD -> Decision.learning(1, secondStep);
      case EASY -> Decision.graduated(easyIntervalDays);
    };
  }

  private Decision secondStepDecision(ReviewRating rating) {
    return switch (rating) {
      case AGAIN -> Decision.learning(0, firstStep);
      case HARD -> Decision.learning(1, secondStep);
      case GOOD -> Decision.graduated(graduatingIntervalDays);
      case EASY -> Decision.graduated(easyIntervalDays);
    };
  }

  private int currentStep(SchedulingState state) {
    return state.fsrsStep() != null && state.fsrsStep() > 0 ? 1 : 0;
  }

  public record Decision(Integer nextStep, Duration delay, Integer graduatingIntervalDays) {
    public static Decision learning(int nextStep, Duration delay) {
      return new Decision(nextStep, delay, null);
    }

    public static Decision graduated(int intervalDays) {
      return new Decision(null, null, intervalDays);
    }

    public boolean graduates() {
      return graduatingIntervalDays != null;
    }
  }
}
