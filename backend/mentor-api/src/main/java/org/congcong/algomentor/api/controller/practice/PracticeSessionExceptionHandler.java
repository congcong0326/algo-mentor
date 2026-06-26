package org.congcong.algomentor.api.controller.practice;

import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;

/**
 * @deprecated HTTP 错误响应由 {@link LocalizedApiExceptionHandler} 统一处理。
 */
@Deprecated(forRemoval = false)
public final class PracticeSessionExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = LocalizedApiExceptionHandler.AUTH_UNAUTHENTICATED_CODE;
  public static final String PRACTICE_MESSAGE_INVALID_CODE =
      LocalizedApiExceptionHandler.PRACTICE_MESSAGE_INVALID_CODE;
  public static final String PRACTICE_PROGRESS_STATUS_INVALID_CODE =
      LocalizedApiExceptionHandler.PRACTICE_PROGRESS_STATUS_INVALID_CODE;

  public PracticeSessionExceptionHandler() {
  }
}
