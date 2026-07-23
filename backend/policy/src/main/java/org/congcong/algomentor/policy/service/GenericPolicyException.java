package org.congcong.algomentor.policy.service;

/** 通用策略可预期失败的领域异常。 */
public class GenericPolicyException extends RuntimeException {

  private final GenericPolicyErrorCode code;

  public GenericPolicyException(GenericPolicyErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public GenericPolicyException(GenericPolicyErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public GenericPolicyErrorCode code() {
    return code;
  }
}
