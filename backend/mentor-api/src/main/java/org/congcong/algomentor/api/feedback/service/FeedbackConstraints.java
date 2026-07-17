package org.congcong.algomentor.api.feedback.service;

/** 反馈 API 和持久化共享的边界限制。 */
public final class FeedbackConstraints {

  public static final int SUBJECT_MAX_LENGTH = 200;
  public static final int CONTENT_MAX_LENGTH = 4_000;
  public static final int SOURCE_PATH_MAX_LENGTH = 500;
  public static final int SOURCE_REQUEST_ID_MAX_LENGTH = 128;
  public static final int SOURCE_RUN_ID_MAX_LENGTH = 80;
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  private FeedbackConstraints() {
  }
}
