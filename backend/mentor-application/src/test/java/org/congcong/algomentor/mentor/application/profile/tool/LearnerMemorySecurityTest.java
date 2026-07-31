package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
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

class LearnerMemorySecurityTest {

  private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

  @Test
  void rejectsCrossUserAndReleasedScopeReferencesWithoutReturningClaimText() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.RecallScopeLease owner = openScope(
        registry, 7L, claim(1L, 7L, "当前用户的公开学习目标"));
    LearnerMemoryRunScopeRegistry.RecallScopeLease otherUser = openScope(
        registry, 8L, claim(2L, 8L, "其他用户的私有学习目标"));
    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);

    JsonNode forged = search.execute(arguments("学习目标")
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF,
            otherUser.snapshot().sections().get(0).sectionRef()), context(owner));

    assertThat(forged.path(LearnerMemoryRecallToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
    assertThat(forged.toString()).doesNotContain("其他用户的私有学习目标", otherUser.scopeRef());

    owner.release();
    JsonNode released = search.execute(arguments("学习目标"), context(owner));
    assertThat(released.path(LearnerMemoryRecallToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.FAILURE_SCOPE_UNAVAILABLE);
    assertThat(released.toString()).doesNotContain("当前用户的公开学习目标", owner.scopeRef());
  }

  private static LearnerMemoryRunScopeRegistry.RecallScopeLease openScope(
      LearnerMemoryRunScopeRegistry registry,
      long userId,
      LearnerMemoryClaimRevision claim
  ) {
    return registry.openRecallScope(
        userId,
        "a".repeat(64),
        "zh-CN",
        List.of(new LearnerMemoryRecallSnapshot.SectionInput(
            "background-goals",
            "学习背景与目标",
            List.of(new LearnerMemoryRecallSnapshot.StatementInput(claim, "用户明确自述", false)))),
        List.of(claim.id()));
  }

  private static LearnerMemoryClaimRevision claim(long id, long userId, String text) {
    return new LearnerMemoryClaimRevision(
        id,
        new UUID(0L, id),
        userId,
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

  private static AgentExecutionContext context(LearnerMemoryRunScopeRegistry.RecallScopeLease lease) {
    return new AgentExecutionContext(
        "security-run",
        1,
        Map.of(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF, lease.scopeRef()),
        false);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode arguments(String query) {
    return JsonNodeFactory.instance.objectNode()
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_QUERY, query)
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT, 20);
  }
}
