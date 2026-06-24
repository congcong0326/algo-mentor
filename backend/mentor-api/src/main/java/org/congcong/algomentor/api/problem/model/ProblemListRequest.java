package org.congcong.algomentor.api.problem.model;

public record ProblemListRequest(
    String keyword,
    ProblemDifficulty difficulty,
    String tag,
    String category,
    ProblemSort sort,
    int page,
    int pageSize,
    ProblemLocale locale
) {

  public static final int DEFAULT_PAGE = 1;
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;

  public ProblemListRequest {
    page = Math.max(DEFAULT_PAGE, page);
    pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    sort = sort == null ? ProblemSort.DEFAULT : sort;
    locale = locale == null ? ProblemLocale.DEFAULT : locale;
    keyword = blankToNull(keyword);
    tag = blankToNull(tag);
    category = blankToNull(category);
  }

  public int offset() {
    return (page - 1) * pageSize;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
