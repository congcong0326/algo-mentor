package org.congcong.algomentor.mentor.application.review;

/**
 * 错题复习跨模块契约常量，避免 scope、schema、metadata key 分散硬编码。
 */
public final class MistakeReviewConstants {

  public static final String QUOTA_SCOPE = "REVIEW_CARD_GEN";
  public static final String CARD_SCHEMA_NAME = "review_card_v1";
  public static final String JUDGE_SCHEMA_NAME = "recall_judgment_v1";
  public static final String REVEAL_HIDE_PREVIOUS = "HIDE_PREVIOUS_CODE_AND_SOLUTION";
  public static final String METADATA_MISTAKE_NOTE_ID = "mistakeNoteId";
  public static final String METADATA_STATEMENT_SUMMARY = "statementSummary";
  public static final String METADATA_TITLE_CN = "titleCn";
  public static final String METADATA_DIFFICULTY = "difficulty";
  public static final int TRANSIENT_NOTE_MAX_CHARS = 2000;
  public static final int PERSISTENT_NOTE_MAX_CHARS = 2000;
  public static final int STATEMENT_SUMMARY_MAX_CHARS = 200;

  private MistakeReviewConstants() {
  }
}
