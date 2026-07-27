package org.congcong.algomentor.ai.governance.routing;

import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;

/** 模型路由在业务执行前拒绝请求时抛出的低敏稳定异常。 */
public class AiModelRouteException extends RuntimeException {

  private final AiGovernanceErrorCode code;

  public AiModelRouteException(AiGovernanceErrorCode code, String message) {
    this(code, message, null);
  }

  public AiModelRouteException(AiGovernanceErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public AiGovernanceErrorCode code() {
    return code;
  }
}
