package org.congcong.algomentor.mentor.application.review;

public final class ReviewContractConstants {

  public static final int NOTE_MARKDOWN_MAX_CHARS = 10_000;
  public static final int STATEMENT_SUMMARY_MAX_CHARS = 200;
  public static final int RECENT_ATTEMPT_LIMIT = 10;

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
