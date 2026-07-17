package org.congcong.algomentor.api.feedback.service;

import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;

/** 输入规范化只处理空白和换行，不压缩正文内部格式。 */
public final class FeedbackInputNormalizer {

  private FeedbackInputNormalizer() {
  }

  public static FeedbackCategory category(String value) {
    return parseEnum(value, FeedbackCategory.class, FeedbackErrorCode.FEEDBACK_CATEGORY_INVALID, "反馈分类不合法。");
  }

  public static FeedbackStatus status(String value) {
    return parseEnum(value, FeedbackStatus.class, FeedbackErrorCode.FEEDBACK_STATUS_INVALID, "反馈状态不合法。");
  }

  public static String subject(String value) {
    String normalized = trimToNull(value);
    if (normalized != null && normalized.length() > FeedbackConstraints.SUBJECT_MAX_LENGTH) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_MESSAGE_INVALID, "反馈主题过长。");
    }
    return normalized;
  }

  public static String content(String value) {
    if (value == null) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_MESSAGE_INVALID, "反馈正文不能为空。");
    }
    String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.trim().isEmpty() || normalized.length() > FeedbackConstraints.CONTENT_MAX_LENGTH) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_MESSAGE_INVALID, "反馈正文长度不合法。");
    }
    return normalized;
  }

  public static String sourcePath(String value) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      return null;
    }
    if (normalized.length() > FeedbackConstraints.SOURCE_PATH_MAX_LENGTH
        || !normalized.startsWith("/")
        || normalized.contains("://")
        || normalized.contains("#")
        || normalized.contains("?")) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_SOURCE_PATH_INVALID, "反馈来源路径不合法。");
    }
    return normalized;
  }

  public static String sourceRequestId(String value) {
    return bounded(value, FeedbackConstraints.SOURCE_REQUEST_ID_MAX_LENGTH, FeedbackErrorCode.FEEDBACK_MESSAGE_INVALID,
        "反馈请求标识过长。");
  }

  public static String sourceRunId(String value) {
    return bounded(value, FeedbackConstraints.SOURCE_RUN_ID_MAX_LENGTH, FeedbackErrorCode.FEEDBACK_SOURCE_RUN_INVALID,
        "反馈运行标识不合法。");
  }

  public static int page(int page) {
    if (page < 1) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_PAGE_INVALID, "分页参数不合法。");
    }
    return page;
  }

  public static int pageSize(int pageSize) {
    if (pageSize < 1 || pageSize > FeedbackConstraints.MAX_PAGE_SIZE) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_PAGE_INVALID, "分页参数不合法。");
    }
    return pageSize;
  }

  private static String bounded(String value, int maxLength, FeedbackErrorCode code, String message) {
    String normalized = trimToNull(value);
    if (normalized != null && normalized.length() > maxLength) {
      throw new FeedbackException(code, message);
    }
    return normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static <E extends Enum<E>> E parseEnum(
      String value,
      Class<E> type,
      FeedbackErrorCode code,
      String message
  ) {
    if (value == null || value.isBlank()) {
      throw new FeedbackException(code, message);
    }
    try {
      return Enum.valueOf(type, value.trim());
    } catch (IllegalArgumentException exception) {
      throw new FeedbackException(code, message);
    }
  }
}
