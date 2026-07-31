package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.junit.jupiter.api.Test;

/** 使用固定脚本替身验证 recall 工具选择，而非依赖外部模型自然语言输出。 */
class LearnerMemoryAgentEvalTest {

  private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

  @Test
  void executesSixFixedBusinessScenariosWithBoundedMemoryToolUse() {
    ScriptedRecallRuntime runtime = new ScriptedRecallRuntime();

    EvalResult directHit = runtime.execute(EvalScenario.DIRECT_HIT);
    assertThat(directHit.toolNames()).isEmpty();
    assertThat(directHit.answerFromMemory()).isTrue();

    EvalResult historyNeeded = runtime.execute(EvalScenario.HISTORY_NEEDED);
    assertThat(historyNeeded.toolNames()).containsExactly(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY);
    assertThat(historyNeeded.result().path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).isNotEmpty();

    EvalResult irrelevant = runtime.execute(EvalScenario.IRRELEVANT);
    assertThat(irrelevant.toolNames()).isEmpty();
    assertThat(irrelevant.answerFromMemory()).isFalse();

    EvalResult longResult = runtime.execute(EvalScenario.LONG_RESULT_RANGE_READ);
    assertThat(longResult.toolNames()).containsExactly(
        LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
        "read_tool_result");
    assertThat(longResult.result().path("content").asText())
        .isNotBlank()
        .hasSizeLessThanOrEqualTo(LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);

    EvalResult empty = runtime.execute(EvalScenario.EMPTY_RESULT);
    assertThat(empty.toolNames()).containsExactly(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY);
    assertThat(empty.result().path(LearnerMemoryRecallToolContracts.FIELD_MESSAGE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.MESSAGE_EMPTY_SEARCH);
    assertThat(empty.answerFromMemory()).isFalse();

    EvalResult sectionNeeded = runtime.execute(EvalScenario.SECTION_NEEDED);
    assertThat(sectionNeeded.toolNames()).containsExactly(LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION);
    assertThat(sectionNeeded.result().path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).isNotEmpty();
  }

  private enum EvalScenario {
    DIRECT_HIT,
    HISTORY_NEEDED,
    IRRELEVANT,
    LONG_RESULT_RANGE_READ,
    EMPTY_RESULT,
    SECTION_NEEDED
  }

  private record EvalResult(List<String> toolNames, JsonNode result, boolean answerFromMemory) {
  }

  private static final class ScriptedRecallRuntime {

    private final LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    private final SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);
    private final ReadLearnerMemorySectionAgentTool readSection = new ReadLearnerMemorySectionAgentTool(registry);

    private EvalResult execute(EvalScenario scenario) {
      List<LearnerMemoryClaimRevision> claims = scenario == EvalScenario.LONG_RESULT_RANGE_READ
          ? java.util.stream.LongStream.rangeClosed(1, 20)
              .mapToObj(id -> claim(id, "长结果" + "x".repeat(590))).toList()
          : List.of(claim(1L, "历史复盘表明需要先确认边界"), claim(2L, "当前目标是准备后端面试"));
      LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(claims, List.of(claims.get(1).id()));
      AgentExecutionContext context = new AgentExecutionContext(
          "eval-run-" + scenario.name(),
          1,
          Map.of(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF, lease.scopeRef()),
          false);
      List<String> toolNames = new ArrayList<>();
      try {
        return switch (scenario) {
          case DIRECT_HIT -> new EvalResult(List.of(), null, !lease.snapshot().directHits().isEmpty());
          case IRRELEVANT -> new EvalResult(List.of(), null, false);
          case HISTORY_NEEDED -> search(toolNames, context, "历史", true);
          case EMPTY_RESULT -> search(toolNames, context, "无关词", false);
          case SECTION_NEEDED -> readSection(toolNames, context, lease.snapshot().sections().get(0).sectionRef());
          case LONG_RESULT_RANGE_READ -> rangeRead(toolNames, context);
        };
      } finally {
        lease.release();
      }
    }

    private EvalResult search(List<String> toolNames, AgentExecutionContext context, String query, boolean answerFromMemory) {
      toolNames.add(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY);
      return new EvalResult(List.copyOf(toolNames), search.execute(searchArguments(query), context), answerFromMemory);
    }

    private EvalResult readSection(List<String> toolNames, AgentExecutionContext context, String sectionRef) {
      toolNames.add(LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION);
      JsonNode result = readSection.execute(JsonNodeFactory.instance.objectNode()
          .put(LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF, sectionRef)
          .put(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT, 20), context);
      return new EvalResult(List.copyOf(toolNames), result, true);
    }

    private EvalResult rangeRead(List<String> toolNames, AgentExecutionContext context) {
      toolNames.add(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY);
      JsonNode result = search.execute(searchArguments("长结果"), context);
      toolNames.add("read_tool_result");
      String content = result.toString();
      return new EvalResult(List.copyOf(toolNames), JsonNodeFactory.instance.objectNode()
          .put("content", content.substring(0, Math.min(content.length(), 8_000))), true);
    }

    private LearnerMemoryRunScopeRegistry.RecallScopeLease openScope(
        List<LearnerMemoryClaimRevision> claims,
        List<Long> directHits
    ) {
      return registry.openRecallScope(
          7L,
          "a".repeat(64),
          "zh-CN",
          List.of(new LearnerMemoryRecallSnapshot.SectionInput(
              "background-goals",
              "学习背景与目标",
              claims.stream().map(claim ->
                  new LearnerMemoryRecallSnapshot.StatementInput(claim, "用户明确自述", false)).toList())),
          directHits);
    }
  }

  private static LearnerMemoryClaimRevision claim(long id, String text) {
    return new LearnerMemoryClaimRevision(
        id,
        new UUID(0L, id),
        7L,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        "%064x".formatted(id),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        NOW,
        null,
        NOW,
        NOW);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode searchArguments(String query) {
    return JsonNodeFactory.instance.objectNode()
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_QUERY, query)
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT, 20);
  }
}
