package org.congcong.algomentor.policy.controller.model;

import java.util.List;
import java.util.Map;

/** 一次性提交同类型所有未删除策略顺序的请求。 */
public record GenericPolicyOrderRequest(List<Long> policyIds, Map<Long, Long> versions) {
}
