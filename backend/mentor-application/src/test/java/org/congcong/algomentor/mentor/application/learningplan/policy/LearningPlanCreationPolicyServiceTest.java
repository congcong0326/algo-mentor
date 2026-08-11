package org.congcong.algomentor.mentor.application.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LearningPlanCreationPolicyServiceTest {

  @Test
  void usesResolvedPolicyForQuotaDateAndRetentionSnapshot() {
    LearningPlanCreationPolicyService service = new LearningPlanCreationPolicyService(
        ignored -> new LearningPlanCreationPolicy(40, 7, 21),
        ZoneId.of("Asia/Shanghai"));
    Instant now = Instant.parse("2026-08-10T16:30:00Z");

    LearningPlanDraftCreationAdmission admission = service.draftAdmission(7L, now);

    assertThat(admission.quotaDate()).hasToString("2026-08-11");
    assertThat(admission.dailyLimit()).isEqualTo(7);
    assertThat(admission.expiresAt()).isEqualTo(Instant.parse("2026-08-31T16:30:00Z"));
    assertThat(service.maxSavedPlans(7L)).isEqualTo(40);
  }

  @Test
  void rejectsPolicyValuesOutsideAbsoluteSafetyBounds() {
    assertThatThrownBy(() -> new LearningPlanCreationPolicy(1_001, 5, 14))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LearningPlanCreationPolicyConstants.MAX_SAVED_PLANS_FIELD);
    assertThatThrownBy(() -> new LearningPlanCreationPolicy(30, 1_001, 14))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LearningPlanCreationPolicyConstants.DAILY_DRAFT_CREATION_LIMIT_FIELD);
    assertThatThrownBy(() -> new LearningPlanCreationPolicy(30, 5, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LearningPlanCreationPolicyConstants.DRAFT_RETENTION_DAYS_FIELD);
  }
}
