package org.congcong.algomentor.policy.controller.model;

import java.util.List;
import org.congcong.algomentor.policy.repository.GenericPolicyPage;

/** 管理端策略列表分页响应。 */
public record GenericPolicyPageResponse(List<GenericPolicyResponse> items, long total, int page, int pageSize) {

  public static GenericPolicyPageResponse from(GenericPolicyPage page) {
    return new GenericPolicyPageResponse(
        page.items().stream().map(GenericPolicyResponse::from).toList(),
        page.total(),
        page.page(),
        page.pageSize());
  }
}
