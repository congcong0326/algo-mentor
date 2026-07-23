package org.congcong.algomentor.policy.service;

/** 已启用策略不能构建运行时快照时抛出，禁止回退为未命中。 */
public final class PolicyCompilationException extends GenericPolicyException {

  private final String typeCode;
  private final long policyId;
  private final long policyVersion;
  private final String targetJavaType;

  public PolicyCompilationException(
      String typeCode,
      long policyId,
      long policyVersion,
      String targetJavaType,
      String message,
      Throwable cause
  ) {
    super(GenericPolicyErrorCode.POLICY_CONTENT_COMPILE_FAILED, message, cause);
    this.typeCode = typeCode;
    this.policyId = policyId;
    this.policyVersion = policyVersion;
    this.targetJavaType = targetJavaType;
  }

  public String typeCode() {
    return typeCode;
  }

  public long policyId() {
    return policyId;
  }

  public long policyVersion() {
    return policyVersion;
  }

  public String targetJavaType() {
    return targetJavaType;
  }
}
