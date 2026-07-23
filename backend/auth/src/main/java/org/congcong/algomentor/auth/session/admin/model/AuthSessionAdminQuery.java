package org.congcong.algomentor.auth.session.admin.model;

/**
 * 已规范化的管理员会话查询条件。
 */
public record AuthSessionAdminQuery(
    int page,
    int pageSize,
    String keyword,
    Long keywordUserId,
    AuthSessionAdminActivityFilter activity
) {

  public long offset() {
    return (long) (page - 1) * pageSize;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

  public String getKeyword() {
    return keyword;
  }

  public Long getKeywordUserId() {
    return keywordUserId;
  }

  public AuthSessionAdminActivityFilter getActivity() {
    return activity;
  }

  public long getOffset() {
    return offset();
  }

  public boolean isActive() {
    return activity == AuthSessionAdminActivityFilter.ACTIVE;
  }

  public boolean isIdle() {
    return activity == AuthSessionAdminActivityFilter.IDLE;
  }
}
