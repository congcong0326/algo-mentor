package org.congcong.algomentor.api.admin.overview;

/** 概览区块独立降级；失败时不以零值替代数据。 */
public record AdminOverviewSection<T>(boolean available, T data, String errorCode) {
  public static <T> AdminOverviewSection<T> available(T data) {
    return new AdminOverviewSection<>(true, data, null);
  }
  public static <T> AdminOverviewSection<T> unavailable() {
    return new AdminOverviewSection<>(false, null, "ADMIN_OVERVIEW_SECTION_UNAVAILABLE");
  }
}
