package org.congcong.algomentor.api.problem.tool;

import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.APPLIED_FILTERS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.COMPANY;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.COMPANY_FREQUENCY_SCORE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.COMPANY_SIGNAL_COUNT;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.CONTENT_STATUS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.DIFFICULTY;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.FRONTEND_DISPLAY_ID;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.FRONTEND_ID;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.ITEMS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.KEYWORD;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LABEL;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LOCALE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.PAGE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.PAGE_SIZE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.RECENCY_BUCKET;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.ROLE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SEARCH_PROBLEMS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SLUG;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SORT;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TAG;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TAGS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TITLE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TOTAL;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSort;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class SearchProblemsTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      SEARCH_PROBLEMS,
      "Search local algorithm problems by keyword, difficulty, tag, sort, and page. "
          + "Use COMPANY_FREQUENCY_DESC for popular or high-frequency interview problems. "
          + "Returns lightweight metadata only.",
      inputSchema(),
      true);

  private final ProblemService problemService;

  public SearchProblemsTool(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    try {
      ProblemListRequest request = request(arguments, context);
      validateTag(request.tag(), request.locale());
      ProblemPage<ProblemListItem> page = problemService.findProblems(request);
      return output(page, request);
    } catch (AgentException exception) {
      throw exception;
    } catch (ProblemService.ProblemRepositoryUnavailableException exception) {
      throw ProblemAgentToolSupport.toolFailure(SEARCH_PROBLEMS, exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      throw ProblemAgentToolSupport.toolFailure(SEARCH_PROBLEMS, "Failed to search problems.", exception);
    }
  }

  private ProblemListRequest request(JsonNode arguments, AgentExecutionContext context) {
    String keyword = ProblemAgentToolSupport.optionalText(arguments, KEYWORD, SEARCH_PROBLEMS);
    ProblemDifficulty difficulty = difficulty(ProblemAgentToolSupport.optionalText(
        arguments,
        DIFFICULTY,
        SEARCH_PROBLEMS));
    String tag = ProblemAgentToolSupport.optionalText(arguments, TAG, SEARCH_PROBLEMS);
    String company = ProblemAgentToolSupport.optionalText(arguments, COMPANY, SEARCH_PROBLEMS);
    String role = ProblemAgentToolSupport.optionalText(arguments, ROLE, SEARCH_PROBLEMS);
    String recencyBucket = ProblemAgentToolSupport.optionalText(arguments, RECENCY_BUCKET, SEARCH_PROBLEMS);
    ProblemSort sort = sort(ProblemAgentToolSupport.optionalText(arguments, SORT, SEARCH_PROBLEMS));
    int page = ProblemAgentToolSupport.optionalInt(
        arguments,
        PAGE,
        ProblemListRequest.DEFAULT_PAGE,
        SEARCH_PROBLEMS);
    int pageSize = ProblemAgentToolSupport.optionalInt(
        arguments,
        PAGE_SIZE,
        ProblemListRequest.DEFAULT_PAGE_SIZE,
        SEARCH_PROBLEMS);
    ProblemLocale locale = locale(arguments, context);
    return new ProblemListRequest(keyword, difficulty, tag, null, company, role, recencyBucket, sort, page, pageSize, locale);
  }

  private ProblemDifficulty difficulty(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return ProblemDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw ProblemAgentToolSupport.toolFailure(
          SEARCH_PROBLEMS,
          "difficulty must be one of EASY, MEDIUM, or HARD.",
          exception);
    }
  }

  private ProblemSort sort(String raw) {
    if (raw == null) {
      return ProblemSort.DEFAULT;
    }
    try {
      return ProblemSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw ProblemAgentToolSupport.toolFailure(
          SEARCH_PROBLEMS,
          "sort must be one of FRONTEND_ID_ASC, FRONTEND_ID_DESC, TITLE_ASC, UPDATED_DESC, or COMPANY_FREQUENCY_DESC.",
          exception);
    }
  }

  private ProblemLocale locale(JsonNode arguments, AgentExecutionContext context) {
    try {
      String trustedLocale = ProblemAgentToolSupport.trustedContentLocale(context);
      return ProblemLocale.parse(trustedLocale == null
          ? ProblemAgentToolSupport.optionalText(arguments, LOCALE, SEARCH_PROBLEMS)
          : trustedLocale);
    } catch (ProblemLocale.UnsupportedProblemLocaleException exception) {
      throw ProblemAgentToolSupport.toolFailure(SEARCH_PROBLEMS, exception.getMessage(), exception);
    }
  }

  private void validateTag(String tag, ProblemLocale locale) {
    if (tag == null) {
      return;
    }
    Set<String> tags = problemService.findProblemFilters(locale).tags().stream()
        .map(ProblemFilterOption::value)
        .collect(Collectors.toUnmodifiableSet());
    if (!tags.contains(tag)) {
      throw ProblemAgentToolSupport.toolFailure(
          SEARCH_PROBLEMS,
          "tag must exactly match a value returned by list_problem_filters.",
          null);
    }
  }

  private JsonNode output(ProblemPage<ProblemListItem> page, ProblemListRequest request) {
    ObjectNode output = JsonNodeFactory.instance.objectNode();
    output.set(ITEMS, items(page));
    output.put(TOTAL, page.total());
    output.put(PAGE, page.page());
    output.put(PAGE_SIZE, page.pageSize());
    output.set(APPLIED_FILTERS, appliedFilters(request));
    return output;
  }

  private ArrayNode items(ProblemPage<ProblemListItem> page) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    for (ProblemListItem item : page.items()) {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put(SLUG, item.slug());
      if (item.frontendId() == null) {
        node.putNull(FRONTEND_ID);
      } else {
        node.put(FRONTEND_ID, item.frontendId());
      }
      ProblemAgentToolSupport.putNullable(node, FRONTEND_DISPLAY_ID, item.frontendDisplayId());
      ProblemAgentToolSupport.putNullable(node, TITLE, item.title());
      node.put(DIFFICULTY, item.difficulty() == null ? null : item.difficulty().name());
      ProblemAgentToolSupport.putNullable(node, CONTENT_STATUS, item.contentStatus());
      if (item.companyFrequencyScore() == null) {
        node.putNull(COMPANY_FREQUENCY_SCORE);
      } else {
        node.put(COMPANY_FREQUENCY_SCORE, item.companyFrequencyScore());
      }
      node.put(COMPANY_SIGNAL_COUNT, item.companySignalCount());
      ArrayNode tags = node.putArray(TAGS);
      for (ProblemTag tag : item.tags()) {
        ObjectNode tagNode = tags.addObject();
        tagNode.put(VALUE, tag.value());
        tagNode.put(LABEL, tag.label());
      }
      nodes.add(node);
    }
    return nodes;
  }

  private ObjectNode appliedFilters(ProblemListRequest request) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ProblemAgentToolSupport.putNullable(node, KEYWORD, request.keyword());
    node.put(DIFFICULTY, request.difficulty() == null ? null : request.difficulty().name());
    ProblemAgentToolSupport.putNullable(node, TAG, request.tag());
    ProblemAgentToolSupport.putNullable(node, COMPANY, request.company());
    ProblemAgentToolSupport.putNullable(node, ROLE, request.role());
    ProblemAgentToolSupport.putNullable(node, RECENCY_BUCKET, request.recencyBucket());
    node.put(SORT, request.sort().name());
    node.put(PAGE, request.page());
    node.put(PAGE_SIZE, request.pageSize());
    node.put(LOCALE, request.locale().value());
    return node;
  }

  private static JsonNode inputSchema() {
    ObjectNode schema = ProblemAgentToolSupport.objectSchema();
    ObjectNode properties = schema.putObject(ProblemAgentToolSupport.PROPERTIES);
    properties.set(KEYWORD, ProblemAgentToolSupport.nullableStringProperty(
        "Keyword matched against title, Chinese title, slug, or frontend id."));
    ObjectNode difficulty = ProblemAgentToolSupport.nullableStringProperty(
        "One of EASY, MEDIUM, HARD. Use JSON null, not the string null, to omit this filter.");
    difficulty.putArray(ProblemAgentToolSupport.ENUM).add("EASY").add("MEDIUM").add("HARD").addNull();
    properties.set(DIFFICULTY, difficulty);
    properties.set(TAG, ProblemAgentToolSupport.nullableStringProperty(
        "Exact tag value returned by list_problem_filters, for example Binary Search. Use null to omit this filter."));
    properties.set(COMPANY, ProblemAgentToolSupport.nullableStringProperty(
        "Exact company slug returned by list_problem_filters. Use null to omit this filter."));
    properties.set(ROLE, ProblemAgentToolSupport.nullableStringProperty(
        "Exact role value returned by list_problem_filters. Use null to omit this filter."));
    properties.set(RECENCY_BUCKET, ProblemAgentToolSupport.nullableStringProperty(
        "Exact recency bucket returned by list_problem_filters. Use null to omit this filter."));
    ObjectNode sort = ProblemAgentToolSupport.nullableStringProperty(
        "One of FRONTEND_ID_ASC, FRONTEND_ID_DESC, TITLE_ASC, UPDATED_DESC, COMPANY_FREQUENCY_DESC. "
            + "Use COMPANY_FREQUENCY_DESC for popular or high-frequency interview problems, or JSON null for the default sort.");
    sort.putArray(ProblemAgentToolSupport.ENUM)
        .add("FRONTEND_ID_ASC")
        .add("FRONTEND_ID_DESC")
        .add("TITLE_ASC")
        .add("UPDATED_DESC")
        .add("COMPANY_FREQUENCY_DESC")
        .addNull();
    properties.set(SORT, sort);
    properties.set(PAGE, ProblemAgentToolSupport.nullableIntegerProperty(
        "Page number, starting at 1. Use null for the default page.",
        1,
        0));
    properties.set(PAGE_SIZE, ProblemAgentToolSupport.nullableIntegerProperty(
        "Items per page. Use null for the default page size.",
        1,
        ProblemListRequest.MAX_PAGE_SIZE));
    ObjectNode locale = ProblemAgentToolSupport.nullableStringProperty(
        "Problem content locale. Use zh-CN/zh for Chinese, en-US/en for English, or null for zh-CN.");
    locale.putArray(ProblemAgentToolSupport.ENUM).add("zh-CN").add("zh").add("en-US").add("en").addNull();
    properties.set(LOCALE, locale);
    ProblemAgentToolSupport.requireAllProperties(schema);
    return schema;
  }
}
