package org.congcong.algomentor.api.problem.tool;

/**
 * 题库 Agent 工具名称、配置前缀和 JSON 字段契约。
 */
public final class ProblemAgentToolNames {

  /**
   * 返回题库可用难度、标签、排序和分类过滤项。
   */
  public static final String LIST_PROBLEM_FILTERS = "list_problem_filters";

  /**
   * 按关键词、难度、标签和排序查询题目轻量列表。
   */
  public static final String SEARCH_PROBLEMS = "search_problems";

  /**
   * 按 slug 读取单题题面和必要元数据。
   */
  public static final String GET_PROBLEM_STATEMENT = "get_problem_statement";

  /**
   * 是否返回过滤项命中数量。
   */
  public static final String INCLUDE_COUNTS = "includeCounts";

  /**
   * 题库题目总数。
   */
  public static final String PROBLEM_COUNT = "problemCount";

  /**
   * 题目难度过滤项。
   */
  public static final String DIFFICULTIES = "difficulties";

  /**
   * 题目标签过滤项。
   */
  public static final String TAGS = "tags";

  /**
   * 题目排序选项。
   */
  public static final String SORTS = "sorts";

  /**
   * 题目分类过滤项。
   */
  public static final String CATEGORIES = "categories";

  /**
   * 公司过滤项。
   */
  public static final String COMPANIES = "companies";

  /**
   * 岗位过滤项。
   */
  public static final String ROLES = "roles";

  /**
   * 时间桶过滤项。
   */
  public static final String RECENCY_BUCKETS = "recencyBuckets";

  /**
   * 给模型使用工具的简短提示。
   */
  public static final String NOTES = "notes";

  /**
   * 通用过滤项值。
   */
  public static final String VALUE = "value";

  /**
   * 当前语言展示名称。
   */
  public static final String LABEL = "label";

  /**
   * 题库内容语言。
   */
  public static final String LOCALE = "locale";

  /**
   * 题目稳定标识。
   */
  public static final String SLUG = "slug";

  /**
   * 分类展示名称。
   */
  public static final String NAME = "name";

  /**
   * 题面读取是否命中。
   */
  public static final String FOUND = "found";

  /**
   * LeetCode 前端编号。
   */
  public static final String FRONTEND_ID = "frontendId";

  /**
   * 支持非数字题号的展示编号。
   */
  public static final String FRONTEND_DISPLAY_ID = "frontendDisplayId";

  /**
   * 题面内容状态。
   */
  public static final String CONTENT_STATUS = "contentStatus";

  /**
   * 公司过滤值。
   */
  public static final String COMPANY = "company";

  /**
   * 岗位过滤值。
   */
  public static final String ROLE = "role";

  /**
   * 时间桶过滤值。
   */
  public static final String RECENCY_BUCKET = "recencyBucket";

  /**
   * 当前过滤条件下的公司频度。
   */
  public static final String COMPANY_FREQUENCY_SCORE = "companyFrequencyScore";

  /**
   * 当前过滤条件下的公司信号数量。
   */
  public static final String COMPANY_SIGNAL_COUNT = "companySignalCount";

  /**
   * 英文题名。
   */
  public static final String TITLE = "title";

  /**
   * 题目难度。
   */
  public static final String DIFFICULTY = "difficulty";

  /**
   * 题面 Markdown。
   */
  public static final String CONTENT_MARKDOWN = "contentMarkdown";

  /**
   * 样例测试用例。
   */
  public static final String SAMPLE_TEST_CASE = "sampleTestCase";

  /**
   * LeetCode 原题链接。
   */
  public static final String LEETCODE_URL = "leetcodeUrl";

  /**
   * 搜索关键词。
   */
  public static final String KEYWORD = "keyword";

  /**
   * 标签过滤值。
   */
  public static final String TAG = "tag";

  /**
   * 排序方式。
   */
  public static final String SORT = "sort";

  /**
   * 页码。
   */
  public static final String PAGE = "page";

  /**
   * 每页数量。
   */
  public static final String PAGE_SIZE = "pageSize";

  /**
   * 题目列表。
   */
  public static final String ITEMS = "items";

  /**
   * 搜索匹配总数。
   */
  public static final String TOTAL = "total";

  /**
   * 工具实际使用的过滤条件。
   */
  public static final String APPLIED_FILTERS = "appliedFilters";

  private ProblemAgentToolNames() {
  }
}
