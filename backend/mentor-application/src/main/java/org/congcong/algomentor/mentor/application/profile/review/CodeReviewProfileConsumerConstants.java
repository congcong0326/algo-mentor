package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;

/** Code Review 观察源的固定批量、范围和治理契约。 */
public final class CodeReviewProfileConsumerConstants {

  public static final int BATCH_SIZE = 5;
  public static final int MAX_DISTINCT_PROBLEMS = 10;
  public static final int MAX_STALE_RETRIES = 1;
  public static final String PROMPT_VERSION = "code-review-profile-v2";
  public static final String SCHEMA_VERSION = "v1";
  public static final String AGENT_TITLE = "code-review-profile-update";
  public static final String BACKGROUND_IDEMPOTENCY_KEY_PREFIX = "code-review-profile:";
  public static final String BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR = ":retry:";
  public static final String METADATA_WINDOW_PROBLEM_COUNT = "codeReviewProfileWindowProblemCount";
  public static final String METADATA_CANDIDATE_COUNT = "codeReviewProfileCandidateCount";
  public static final String QUOTA_SCOPE = "LEARNER_PROFILE_CODE_REVIEW";
  public static final AiPurpose AI_PURPOSE = AiPurpose.LEARNING_CHAT;
  public static final List<LearnerProfileDimension> GENERAL_DIMENSIONS = List.of(
      LearnerProfileDimension.PROBLEM_SOLVING_APPROACH,
      LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN);

  private CodeReviewProfileConsumerConstants() {
  }
}
