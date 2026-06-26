package org.congcong.algomentor.api.controller.learningplan;

import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;

/**
 * @deprecated HTTP 错误响应由 {@link LocalizedApiExceptionHandler} 统一处理。
 */
@Deprecated(forRemoval = false)
public final class LearningPlanExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = LocalizedApiExceptionHandler.AUTH_UNAUTHENTICATED_CODE;

  public LearningPlanExceptionHandler() {
  }
}
