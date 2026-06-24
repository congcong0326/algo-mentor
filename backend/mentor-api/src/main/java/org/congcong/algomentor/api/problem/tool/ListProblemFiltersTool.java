package org.congcong.algomentor.api.problem.tool;

import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.CATEGORIES;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.DIFFICULTIES;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.INCLUDE_COUNTS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LABEL;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LIST_PROBLEM_FILTERS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LOCALE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.NAME;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.NOTES;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.PROBLEM_COUNT;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SLUG;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SORTS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TAGS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.api.problem.model.ProblemCategoryFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemSort;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class ListProblemFiltersTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LIST_PROBLEM_FILTERS,
      "List supported problem search filters, including difficulties, tags, sorts, and categories.",
      inputSchema(),
      true);

  private final ProblemService problemService;

  public ListProblemFiltersTool(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    try {
      boolean includeCounts = ProblemAgentToolSupport.optionalBoolean(
          arguments,
          INCLUDE_COUNTS,
          true,
          LIST_PROBLEM_FILTERS);
      ProblemLocale locale = locale(arguments);
      ProblemFilters filters = problemService.findProblemFilters(locale);
      return output(filters, includeCounts);
    } catch (AgentException exception) {
      throw exception;
    } catch (ProblemService.ProblemRepositoryUnavailableException exception) {
      throw ProblemAgentToolSupport.toolFailure(LIST_PROBLEM_FILTERS, exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      throw ProblemAgentToolSupport.toolFailure(
          LIST_PROBLEM_FILTERS,
          "Failed to list problem filters.",
          exception);
    }
  }

  private JsonNode output(ProblemFilters filters, boolean includeCounts) {
    ObjectNode output = JsonNodeFactory.instance.objectNode();
    output.put(PROBLEM_COUNT, filters.problemCount());
    output.set(DIFFICULTIES, filterOptions(filters.difficulties(), includeCounts));
    output.set(TAGS, filterOptions(filters.tags(), includeCounts));
    output.set(SORTS, sorts());
    output.set(CATEGORIES, categories(filters, includeCounts));
    output.set(NOTES, notes(filters));
    return output;
  }

  private ArrayNode filterOptions(Iterable<ProblemFilterOption> options, boolean includeCounts) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    for (ProblemFilterOption option : options) {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put(VALUE, option.value());
      node.put(LABEL, option.label());
      if (includeCounts) {
        node.put(PROBLEM_COUNT, option.problemCount());
      }
      nodes.add(node);
    }
    return nodes;
  }

  private ArrayNode categories(ProblemFilters filters, boolean includeCounts) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    for (ProblemCategoryFilterOption category : filters.categories()) {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put(SLUG, category.slug());
      node.put(NAME, category.name());
      if (includeCounts) {
        node.put(PROBLEM_COUNT, category.problemCount());
      }
      nodes.add(node);
    }
    return nodes;
  }

  private ArrayNode sorts() {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    for (ProblemSort sort : ProblemSort.values()) {
      nodes.add(sort.name());
    }
    return nodes;
  }

  private ArrayNode notes(ProblemFilters filters) {
    ArrayNode nodes = JsonNodeFactory.instance.arrayNode();
    nodes.add("Use difficulty, tag, and sort values exactly as returned by this tool.");
    if (filters.categories().isEmpty()) {
      nodes.add("No category filters are available yet; prefer tag-based search.");
    }
    return nodes;
  }

  private static JsonNode inputSchema() {
    ObjectNode schema = ProblemAgentToolSupport.objectSchema();
    ObjectNode properties = schema.putObject(ProblemAgentToolSupport.PROPERTIES);
    properties.set(INCLUDE_COUNTS, ProblemAgentToolSupport.nullableBooleanProperty(
        "Whether to include problem counts for each returned filter value. Use null to apply the default true."));
    ObjectNode locale = ProblemAgentToolSupport.nullableStringProperty(
        "Problem content locale. Use zh-CN/zh for Chinese, en-US/en for English, or null for zh-CN.");
    locale.putArray(ProblemAgentToolSupport.ENUM).add("zh-CN").add("zh").add("en-US").add("en").addNull();
    properties.set(LOCALE, locale);
    ProblemAgentToolSupport.requireAllProperties(schema);
    return schema;
  }

  private ProblemLocale locale(JsonNode arguments) {
    try {
      return ProblemLocale.parse(ProblemAgentToolSupport.optionalText(arguments, LOCALE, LIST_PROBLEM_FILTERS));
    } catch (ProblemLocale.UnsupportedProblemLocaleException exception) {
      throw ProblemAgentToolSupport.toolFailure(LIST_PROBLEM_FILTERS, exception.getMessage(), exception);
    }
  }
}
