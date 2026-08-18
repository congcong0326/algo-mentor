package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;

/** Code Review 观察源的固定批量、范围和治理契约。 */
public final class LearnerMemoryCodeReviewConsumerConstants {

  public static final int BATCH_SIZE = 5;
  public static final int MAX_DISTINCT_PROBLEMS = 10;
  public static final int MAX_STALE_RETRIES = 1;
  public static final String PROMPT_VERSION = "code-review-claim-update-v4";
  public static final String SCHEMA_VERSION = "v2";
  public static final String AGENT_TITLE = "code-review-profile-update";
  public static final String BACKGROUND_IDEMPOTENCY_KEY_PREFIX = "code-review-profile:";
  public static final String BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR = ":retry:";
  /** 同一批次首次证据校验失败后的受限修复调用幂等键后缀。 */
  public static final String BACKGROUND_EVIDENCE_REPAIR_IDEMPOTENCY_KEY_SUFFIX = ":evidence-repair";
  public static final String METADATA_WINDOW_PROBLEM_COUNT = "learnerMemoryCodeReviewWindowProblemCount";
  public static final String METADATA_INITIAL_ACTIVE_CLAIM_COUNT = "learnerMemoryCodeReviewInitialActiveClaimCount";
  public static final String METADATA_CAPACITY_STATE = "learnerMemoryCodeReviewCapacityState";
  public static final String QUOTA_SCOPE = "LEARNER_PROFILE_CODE_REVIEW";
  public static final AiPurpose AI_PURPOSE = AiPurpose.LEARNING_CHAT;
  public static final List<LearnerMemoryClaimDimension> GENERAL_DIMENSIONS = List.of(
      LearnerMemoryClaimDimension.PROBLEM_SOLVING_APPROACH,
      LearnerMemoryClaimDimension.IMPLEMENTATION_AND_ERROR_PATTERN,
      LearnerMemoryClaimDimension.REVIEW_AND_GROWTH_PERFORMANCE);

  private LearnerMemoryCodeReviewConsumerConstants() {
  }
}
