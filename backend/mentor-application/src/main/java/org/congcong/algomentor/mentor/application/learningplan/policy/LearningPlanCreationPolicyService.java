package org.congcong.algomentor.mentor.application.learningplan.policy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 将有效用户策略转换为草案准入和正式计划上限快照。 */
public class LearningPlanCreationPolicyService {

  private final LearningPlanCreationPolicyResolver resolver;
  private final ZoneId quotaZone;

  public LearningPlanCreationPolicyService() {
    this(LearningPlanCreationPolicyResolver.defaults(), ZoneId.of("UTC"));
  }

  public LearningPlanCreationPolicyService(
      LearningPlanCreationPolicyResolver resolver,
      ZoneId quotaZone
  ) {
    this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    this.quotaZone = Objects.requireNonNull(quotaZone, "quotaZone must not be null");
  }

  public LearningPlanDraftCreationAdmission draftAdmission(long userId, Instant now) {
    LearningPlanCreationPolicy policy = resolve(userId);
    Instant effectiveNow = Objects.requireNonNull(now, "now must not be null");
    return new LearningPlanDraftCreationAdmission(
        LocalDate.ofInstant(effectiveNow, quotaZone),
        policy.dailyDraftCreationLimit(),
        effectiveNow.plus(policy.draftRetentionDays(), ChronoUnit.DAYS));
  }

  public int maxSavedPlans(long userId) {
    return resolve(userId).maxSavedPlans();
  }

  private LearningPlanCreationPolicy resolve(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return Objects.requireNonNull(resolver.resolve(userId), "resolved policy must not be null");
  }
}
