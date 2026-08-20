package org.congcong.algomentor.mentor.application.review;

public final class ReviewContractConstants {

  public static final int NOTE_MARKDOWN_MAX_CHARS = 10_000;
  /** Agent 与编辑器追加笔记段落时使用的稳定 Markdown 分隔符。 */
  public static final String NOTE_MARKDOWN_APPEND_SEPARATOR = "\n\n";
  public static final int STATEMENT_SUMMARY_MAX_CHARS = 200;
  public static final int RECENT_ATTEMPT_LIMIT = 10;
  /** 复习中心复习卡列表固定每页展示数量。 */
  public static final int REVIEW_CARD_LIST_PAGE_SIZE = 10;
  /** 复习中心每题最多展示的正式代码 Review 索引条数。 */
  public static final int RECENT_CODE_REVIEW_INDEX_LIMIT = 10;

  public static final String METADATA_LATEST_REVIEW_ID = "latestReviewId";
  public static final String METADATA_LATEST_REVIEW_SCORE = "latestReviewScore";
  public static final String METADATA_LATEST_REVIEW_PASSED = "latestReviewPassed";
  public static final String METADATA_DEDUCTION_REASONS = "deductionReasons";
  public static final String METADATA_IMPROVEMENT_SUGGESTIONS = "improvementSuggestions";
  public static final String METADATA_LANGUAGE = "language";
  public static final String METADATA_LOW_CONFIDENCE = "lowConfidence";
  public static final String METADATA_SEED_BUCKET = "seedBucket";
  public static final String METADATA_INITIAL_RATING = "initialRating";
  public static final String METADATA_SOURCE = "source";
  public static final String METADATA_STATEMENT_SUMMARY = "statementSummary";
  public static final String METADATA_TITLE_CN = "titleCn";
  public static final String METADATA_DIFFICULTY = "difficulty";

  private ReviewContractConstants() {
  }
}
