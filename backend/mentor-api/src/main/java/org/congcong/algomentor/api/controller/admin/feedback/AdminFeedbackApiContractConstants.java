package org.congcong.algomentor.api.controller.admin.feedback;

/** 管理员反馈 API 的固定路径契约。 */
public final class AdminFeedbackApiContractConstants {
  public static final String BASE_PATH = "/api/admin/feedback";
  public static final String THREAD_ID_PATH = "/{threadId}";
  public static final String MESSAGES_PATH = THREAD_ID_PATH + "/messages";
  public static final String READ_PATH = THREAD_ID_PATH + "/read";
  public static final String STATUS_PATH = THREAD_ID_PATH + "/status";
  private AdminFeedbackApiContractConstants() { }
}
