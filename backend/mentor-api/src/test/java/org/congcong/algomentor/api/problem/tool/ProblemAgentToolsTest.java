package org.congcong.algomentor.api.problem.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.api.problem.model.ProblemCategoryFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProblemAgentToolsTest {

  private final ProblemService problemService = mock(ProblemService.class);

  @Test
  void problemToolSpecsUseStrictRequiredSchemas() {
    List<AgentTool> tools = List.of(
        new ListProblemFiltersTool(problemService),
        new SearchProblemsTool(problemService),
        new GetProblemStatementTool(problemService));

    for (AgentTool tool : tools) {
      assertStrictSchemaRequiresAllProperties(tool);
    }

    JsonNode listSchema = tools.get(0).spec().inputSchema();
    JsonNode includeCountsType = listSchema.path("properties").path("includeCounts").path("type");
    assertThat(includeCountsType.get(0).asText()).isEqualTo("boolean");
    assertThat(includeCountsType.get(1).asText()).isEqualTo("null");

    JsonNode searchSchema = tools.get(1).spec().inputSchema();
    JsonNode pageType = searchSchema.path("properties").path("page").path("type");
    assertThat(pageType.get(0).asText()).isEqualTo("integer");
    assertThat(pageType.get(1).asText()).isEqualTo("null");
    JsonNode difficultyEnum = searchSchema.path("properties").path("difficulty").path("enum");
    assertThat(difficultyEnum).hasSize(4);
    assertThat(difficultyEnum.get(0).asText()).isEqualTo("EASY");
    assertThat(difficultyEnum.get(1).asText()).isEqualTo("MEDIUM");
    assertThat(difficultyEnum.get(2).asText()).isEqualTo("HARD");
    assertThat(difficultyEnum.get(3).isNull()).isTrue();
  }

  @Test
  void listProblemFiltersReturnsFiltersSortsAndNotes() {
    when(problemService.findProblemFilters(ProblemLocale.DEFAULT)).thenReturn(new ProblemFilters(
        2,
        List.of(
            new ProblemFilterOption("EASY", "EASY", 1),
            new ProblemFilterOption("MEDIUM", "MEDIUM", 1),
            new ProblemFilterOption("HARD", "HARD", 0)),
        List.of(
            new ProblemFilterOption("array", "数组", 2),
            new ProblemFilterOption("hash-table", "哈希表", 1)),
        List.of(new ProblemCategoryFilterOption("classic", "经典题", 2))));

    JsonNode output = new ListProblemFiltersTool(problemService).execute(null, null);

    assertThat(output.path("problemCount").asLong()).isEqualTo(2);
    assertThat(output.path("difficulties")).hasSize(3);
    assertThat(output.path("difficulties").get(0).path("value").asText()).isEqualTo("EASY");
    assertThat(output.path("difficulties").get(0).path("label").asText()).isEqualTo("EASY");
    assertThat(output.path("difficulties").get(0).path("problemCount").asLong()).isEqualTo(1);
    assertThat(output.path("tags")).extracting(tag -> tag.path("value").asText())
        .containsExactly("array", "hash-table");
    assertThat(output.path("tags")).extracting(tag -> tag.path("label").asText())
        .containsExactly("数组", "哈希表");
    assertThat(output.path("sorts")).extracting(JsonNode::asText)
        .contains("FRONTEND_ID_ASC", "UPDATED_DESC");
    assertThat(output.path("categories").get(0).path("slug").asText()).isEqualTo("classic");
    assertThat(output.path("notes")).isNotEmpty();
  }

  @Test
  void listProblemFiltersCanOmitPerFilterCounts() {
    when(problemService.findProblemFilters(ProblemLocale.DEFAULT)).thenReturn(new ProblemFilters(
        1,
        List.of(new ProblemFilterOption("EASY", "EASY", 1)),
        List.of(new ProblemFilterOption("array", "数组", 1)),
        List.of()));
    ObjectNode arguments = JsonNodeFactory.instance.objectNode().put("includeCounts", false);

    JsonNode output = new ListProblemFiltersTool(problemService).execute(arguments, null);

    assertThat(output.path("problemCount").asLong()).isEqualTo(1);
    assertThat(output.path("difficulties").get(0).has("problemCount")).isFalse();
    assertThat(output.path("tags").get(0).has("problemCount")).isFalse();
    assertThat(output.path("notes")).extracting(JsonNode::asText)
        .contains("No category filters are available yet; prefer tag-based search.");
  }

  @Test
  void searchProblemsUsesValidatedFiltersAndReturnsLightweightItems() {
    when(problemService.findProblemFilters(ProblemLocale.ZH_CN)).thenReturn(new ProblemFilters(
        1,
        List.of(new ProblemFilterOption("EASY", "EASY", 1)),
        List.of(new ProblemFilterOption("hash-table", "哈希表", 1)),
        List.of()));
    when(problemService.findProblems(any())).thenReturn(new ProblemPage<>(List.of(
        new ProblemListItem(
            "two-sum",
            1,
            "两数之和",
            ProblemDifficulty.EASY,
            List.of(new ProblemTag("array", "数组"), new ProblemTag("hash-table", "哈希表")))
    ), 1, 1, 5));
    ObjectNode arguments = JsonNodeFactory.instance.objectNode()
        .put("keyword", "sum")
        .put("difficulty", "EASY")
        .put("tag", "hash-table")
        .put("sort", "TITLE_ASC")
        .put("locale", "zh-CN")
        .put("page", 1)
        .put("pageSize", 5);

    JsonNode output = new SearchProblemsTool(problemService).execute(arguments, null);

    assertThat(output.path("total").asLong()).isEqualTo(1);
    assertThat(output.path("page").asInt()).isEqualTo(1);
    assertThat(output.path("pageSize").asInt()).isEqualTo(5);
    assertThat(output.path("items").get(0).path("slug").asText()).isEqualTo("two-sum");
    assertThat(output.path("items").get(0).path("title").asText()).isEqualTo("两数之和");
    assertThat(output.path("items").get(0).has("titleCn")).isFalse();
    assertThat(output.path("items").get(0).path("tags").get(0).path("value").asText()).isEqualTo("array");
    assertThat(output.path("items").get(0).path("tags").get(0).path("label").asText()).isEqualTo("数组");
    assertThat(output.path("items").get(0).has("contentMarkdown")).isFalse();
    assertThat(output.path("items").get(0).has("python3Template")).isFalse();
    assertThat(output.path("appliedFilters").path("tag").asText()).isEqualTo("hash-table");
    assertThat(output.path("appliedFilters").path("locale").asText()).isEqualTo("zh-CN");

    ArgumentCaptor<ProblemListRequest> requestCaptor = ArgumentCaptor.forClass(ProblemListRequest.class);
    verify(problemService).findProblems(requestCaptor.capture());
    ProblemListRequest request = requestCaptor.getValue();
    assertThat(request.keyword()).isEqualTo("sum");
    assertThat(request.difficulty()).isEqualTo(ProblemDifficulty.EASY);
    assertThat(request.tag()).isEqualTo("hash-table");
    assertThat(request.category()).isNull();
    assertThat(request.pageSize()).isEqualTo(5);
    assertThat(request.locale()).isEqualTo(ProblemLocale.ZH_CN);
  }

  @Test
  void searchProblemsRejectsUnknownTag() {
    when(problemService.findProblemFilters(ProblemLocale.DEFAULT)).thenReturn(new ProblemFilters(
        1,
        List.of(new ProblemFilterOption("EASY", "EASY", 1)),
        List.of(new ProblemFilterOption("hash-table", "哈希表", 1)),
        List.of()));
    ObjectNode arguments = JsonNodeFactory.instance.objectNode().put("tag", "HashMap");

    assertThatThrownBy(() -> new SearchProblemsTool(problemService).execute(arguments, null))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception).hasMessage("tag must exactly match a value returned by list_problem_filters.");
        });
  }

  @Test
  void getProblemStatementReturnsStatementWithoutInternalImportFields() {
    when(problemService.findProblemBySlug("two-sum", ProblemLocale.EN_US)).thenReturn(java.util.Optional.of(new ProblemDetail(
        "two-sum",
        1,
        "Two Sum",
        ProblemDifficulty.EASY,
        List.of(new ProblemTag("array", "Array"), new ProblemTag("hash-table", "Hash Table")),
        "# Two Sum",
        "https://leetcode.com/problems/two-sum/",
        "[2,7,11,15]\n9",
        "class Solution:\n    pass",
        "abc123")));
    ObjectNode arguments = JsonNodeFactory.instance.objectNode()
        .put("slug", "two-sum")
        .put("locale", "en-US");

    JsonNode output = new GetProblemStatementTool(problemService).execute(arguments, null);

    assertThat(output.path("found").asBoolean()).isTrue();
    assertThat(output.path("slug").asText()).isEqualTo("two-sum");
    assertThat(output.path("title").asText()).isEqualTo("Two Sum");
    assertThat(output.has("titleCn")).isFalse();
    assertThat(output.path("tags").get(0).path("value").asText()).isEqualTo("array");
    assertThat(output.path("tags").get(0).path("label").asText()).isEqualTo("Array");
    assertThat(output.path("contentMarkdown").asText()).isEqualTo("# Two Sum");
    assertThat(output.path("sampleTestCase").asText()).isEqualTo("[2,7,11,15]\n9");
    assertThat(output.has("python3Template")).isFalse();
    assertThat(output.has("sourceCommit")).isFalse();
  }

  @Test
  void getProblemStatementReturnsFoundFalseForUnknownSlug() {
    when(problemService.findProblemBySlug("missing", ProblemLocale.DEFAULT)).thenReturn(java.util.Optional.empty());
    ObjectNode arguments = JsonNodeFactory.instance.objectNode().put("slug", "missing");

    JsonNode output = new GetProblemStatementTool(problemService).execute(arguments, null);

    assertThat(output.path("found").asBoolean()).isFalse();
    assertThat(output.path("slug").asText()).isEqualTo("missing");
  }

  private void assertStrictSchemaRequiresAllProperties(AgentTool tool) {
    JsonNode schema = tool.spec().inputSchema();
    assertThat(tool.spec().strict()).isTrue();
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    List<String> properties = new ArrayList<>();
    schema.path("properties").fieldNames().forEachRemaining(properties::add);
    List<String> required = new ArrayList<>();
    schema.path("required").forEach(node -> required.add(node.asText()));
    assertThat(required).containsExactlyElementsOf(properties);
  }
}
