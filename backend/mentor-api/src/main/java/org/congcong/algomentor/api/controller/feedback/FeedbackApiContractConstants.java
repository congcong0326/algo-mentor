package org.congcong.algomentor.api.controller.feedback;

/** 普通用户反馈 API 的固定路径契约。 */
public final class FeedbackApiContractConstants {
  public static final String BASE_PATH = "/api/feedback";
  public static final String THREAD_ID_PATH = "/{threadId}";
  public static final String MESSAGES_PATH = THREAD_ID_PATH + "/messages";
  public static final String READ_PATH = THREAD_ID_PATH + "/read";
  private FeedbackApiContractConstants() { }
}
