package org.congcong.algomentor.api.knowledge.model;

/** 评价前后快照的 JSON 字段；读写与幂等重放共用。 */
public final class KnowledgeScheduleFields {
  private KnowledgeScheduleFields() {}

  public static final String REPETITIONS = "repetitions";
  public static final String INTERVAL_DAYS = "intervalDays";
  public static final String LAPSES = "lapses";
  public static final String FSRS_STATE = "fsrsState";
  public static final String FSRS_STEP = "fsrsStep";
  public static final String FSRS_STABILITY = "fsrsStability";
  public static final String FSRS_DIFFICULTY = "fsrsDifficulty";
  public static final String DUE_AT = "dueAt";
  public static final String FIRST_REVIEW = "firstReview";
}
