package org.congcong.algomentor.api.controller.problem;

import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;

/**
 * @deprecated HTTP 错误响应由 {@link LocalizedApiExceptionHandler} 统一处理。
 */
@Deprecated(forRemoval = false)
public final class ProblemExceptionHandler {

  public static final String PROBLEM_NOT_FOUND_CODE = LocalizedApiExceptionHandler.PROBLEM_NOT_FOUND_CODE;
  public static final String UNSUPPORTED_PROBLEM_LOCALE_CODE =
      LocalizedApiExceptionHandler.UNSUPPORTED_PROBLEM_LOCALE_CODE;

  public ProblemExceptionHandler() {
  }
}
