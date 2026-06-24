package org.congcong.algomentor.api.problem.tool;

import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.CONTENT_MARKDOWN;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.DIFFICULTY;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.FOUND;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.FRONTEND_ID;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.GET_PROBLEM_STATEMENT;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LABEL;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LEETCODE_URL;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.LOCALE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SAMPLE_TEST_CASE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.SLUG;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TAGS;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.TITLE;
import static org.congcong.algomentor.api.problem.tool.ProblemAgentToolNames.VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class GetProblemStatementTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      GET_PROBLEM_STATEMENT,
      "Get one local algorithm problem statement and metadata by slug. Does not return solution templates.",
      inputSchema(),
      true);

  private final ProblemService problemService;

  public GetProblemStatementTool(ProblemService problemService) {
    this.problemService = problemService;
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String slug = ProblemAgentToolSupport.requiredText(arguments, SLUG, GET_PROBLEM_STATEMENT);
    ProblemLocale locale = locale(arguments);
    try {
      Optional<ProblemDetail> problem = problemService.findProblemBySlug(slug, locale);
      return problem.map(this::found).orElseGet(() -> notFound(slug));
    } catch (AgentException exception) {
      throw exception;
    } catch (ProblemService.ProblemRepositoryUnavailableException exception) {
      throw ProblemAgentToolSupport.toolFailure(GET_PROBLEM_STATEMENT, exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      throw ProblemAgentToolSupport.toolFailure(
          GET_PROBLEM_STATEMENT,
          "Failed to get problem statement.",
          exception);
    }
  }

  private JsonNode found(ProblemDetail problem) {
    ObjectNode output = JsonNodeFactory.instance.objectNode();
    output.put(FOUND, true);
    output.put(SLUG, problem.slug());
    if (problem.frontendId() == null) {
      output.putNull(FRONTEND_ID);
    } else {
      output.put(FRONTEND_ID, problem.frontendId());
    }
    ProblemAgentToolSupport.putNullable(output, TITLE, problem.title());
    output.put(DIFFICULTY, problem.difficulty() == null ? null : problem.difficulty().name());
    ArrayNode tags = output.putArray(TAGS);
    for (ProblemTag tag : problem.tags()) {
      ObjectNode tagNode = tags.addObject();
      tagNode.put(VALUE, tag.value());
      tagNode.put(LABEL, tag.label());
    }
    ProblemAgentToolSupport.putNullable(output, CONTENT_MARKDOWN, problem.contentMarkdown());
    ProblemAgentToolSupport.putNullable(output, SAMPLE_TEST_CASE, problem.sampleTestCase());
    ProblemAgentToolSupport.putNullable(output, LEETCODE_URL, problem.leetcodeUrl());
    return output;
  }

  private JsonNode notFound(String slug) {
    ObjectNode output = JsonNodeFactory.instance.objectNode();
    output.put(FOUND, false);
    output.put(SLUG, slug);
    return output;
  }

  private static JsonNode inputSchema() {
    ObjectNode schema = ProblemAgentToolSupport.objectSchema();
    ObjectNode properties = schema.putObject(ProblemAgentToolSupport.PROPERTIES);
    properties.set(SLUG, ProblemAgentToolSupport.stringProperty(
        "Stable problem slug, for example two-sum."));
    ObjectNode locale = ProblemAgentToolSupport.nullableStringProperty(
        "Problem content locale. Use zh-CN/zh for Chinese, en-US/en for English, or null for zh-CN.");
    locale.putArray(ProblemAgentToolSupport.ENUM).add("zh-CN").add("zh").add("en-US").add("en").addNull();
    properties.set(LOCALE, locale);
    ProblemAgentToolSupport.requireAllProperties(schema);
    return schema;
  }

  private ProblemLocale locale(JsonNode arguments) {
    try {
      return ProblemLocale.parse(ProblemAgentToolSupport.optionalText(arguments, LOCALE, GET_PROBLEM_STATEMENT));
    } catch (ProblemLocale.UnsupportedProblemLocaleException exception) {
      throw ProblemAgentToolSupport.toolFailure(GET_PROBLEM_STATEMENT, exception.getMessage(), exception);
    }
  }
}
