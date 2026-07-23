package org.congcong.algomentor.policy.controller;

/** 通用策略 HTTP 路径与跨端字段契约。 */
public final class PolicyApiContractConstants {

  public static final String ADMIN_POLICIES_BASE_PATH = "/api/admin/policies";
  public static final String ADMIN_POLICY_ORDER_PATH = "/api/admin/policy-types/{typeCode}/order";
  public static final String EFFECTIVE_POLICY_PATH = "/api/policies/{typeCode}/effective";

  private PolicyApiContractConstants() {
  }
}
