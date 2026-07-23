package org.congcong.algomentor.policy.repository;

import org.congcong.algomentor.policy.model.GenericPolicyStatus;

/** 管理列表的数据库查询条件，已删除记录不参与查询。 */
public record GenericPolicySearchQuery(
    String typeCode,
    GenericPolicyStatus status,
    String keyword,
    int page,
    int pageSize
) {

  public GenericPolicySearchQuery {
    if (page < 1 || pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("page must be positive and pageSize must be between 1 and 100");
    }
  }

  public int offset() {
    return (page - 1) * pageSize;
  }
}
