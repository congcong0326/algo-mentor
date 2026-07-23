package org.congcong.algomentor.policy.service;

/** 运行时缓存、关系查询或内容编译失败，不能伪装为没有命中策略。 */
public final class PolicyResolutionException extends GenericPolicyException {

  public PolicyResolutionException(String message, Throwable cause) {
    super(GenericPolicyErrorCode.POLICY_RESOLUTION_FAILED, message, cause);
  }
}
