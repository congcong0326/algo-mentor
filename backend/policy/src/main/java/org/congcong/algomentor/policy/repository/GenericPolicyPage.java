package org.congcong.algomentor.policy.repository;

import java.util.List;
import org.congcong.algomentor.policy.model.GenericPolicy;

/** 管理策略列表分页结果。 */
public record GenericPolicyPage(List<GenericPolicy> items, long total, int page, int pageSize) {

  public GenericPolicyPage {
    items = List.copyOf(items);
  }
}
