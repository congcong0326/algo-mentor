package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryEvidenceRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryDirectHitSelector;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.tool.GetLearnerMemoryEvidenceAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRecallToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.congcong.algomentor.mentor.application.profile.tool.SearchLearnerMemoryAgentTool;
import org.junit.jupiter.api.Test;

class LearnerMemoryRecallEndToEndIT extends PostgresIntegrationTestSupport {

  private final LearnerMemoryClaimTextHasher textHasher = new LearnerMemoryClaimTextHasher();

  @Test
  void readsOnlyActiveClaimsInsideTheOpenedPostgresBackedSnapshot() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    Repositories repositories = repositories();
    insertClaim(repositories, userId, "准备后端面试");
    insertClaim(repositories, otherUserId, "其他用户的隐私目标");
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRecallService recallService = new LearnerMemoryRecallService(
        new LearnerMemoryClaimQueryService(repositories.claims(), new LearnerMemoryClaimSnapshotFactory()),
        TrustedProblemTagCatalog.empty(),
        new LearnerMemorySectionCatalog(),
        new LearnerMemoryDirectHitSelector(),
        registry);

    LearnerMemoryRecallService.OpenedSnapshot current = recallService.openSnapshot(
        userId, "我在准备后端面试", "practice-problem", "zh-CN");
    LearnerMemoryRecallService.OpenedSnapshot other = recallService.openSnapshot(
        otherUserId, "", "practice-problem", "zh-CN");
    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);
    GetLearnerMemoryEvidenceAgentTool evidence = new GetLearnerMemoryEvidenceAgentTool(
        registry, repositories.evidence());

    var result = search.execute(
        JsonNodeFactory.instance.objectNode().put("query", "后端").put("limit", 20), context(current));
    assertThat(result.path("items").toString()).contains("准备后端面试").doesNotContain("其他用户的隐私目标");
    String statementRef = result.path("items").get(0).path("statementRef").asText();
    var evidenceResult = evidence.execute(
        JsonNodeFactory.instance.objectNode().put("statementRef", statementRef).put("limit", 20), context(current));
    assertThat(evidenceResult.path("items")).isEmpty();

    var forged = search.execute(JsonNodeFactory.instance.objectNode()
        .put("query", "后端")
        .put("sectionRef", other.snapshot().sections().get(0).sectionRef())
        .put("limit", 20), context(current));
    assertThat(forged.path("failureCode").asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
    assertThat(forged.toString()).doesNotContain("其他用户的隐私目标");

    current.lease().release();
    other.lease().release();
  }

  private Repositories repositories() throws Exception {
    var template = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml");
    LearnerMemoryMapper mapper = template.getMapper(LearnerMemoryMapper.class);
    return new Repositories(
        new MyBatisLearnerMemoryClaimRepository(mapper),
        new MyBatisLearnerMemoryUpdateRunRepository(mapper),
        new MyBatisLearnerMemoryEvidenceRepository(mapper));
  }

  private void insertClaim(Repositories repositories, long userId, String text) {
    LearnerMemoryUpdateRun run = repositories.runs().create(new LearnerMemoryUpdateRunDraft(
        userId,
        LearnerMemoryRunContract.Trigger.DECLARED_FACT,
        "recall-tool-it-" + UUID.randomUUID(),
        "v1",
        "v1",
        1,
        Instant.now()));
    repositories.claims().insert(new LearnerMemoryClaimRevisionDraft(
        UUID.randomUUID(),
        userId,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        textHasher.hash(text),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        run.id(),
        null,
        Instant.now(),
        null));
  }

  private AgentExecutionContext context(LearnerMemoryRecallService.OpenedSnapshot opened) {
    return new AgentExecutionContext(
        "recall-tool-it",
        1,
        java.util.Map.of(
            LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF,
            opened.snapshot().scopeRef()),
        false);
  }

  private record Repositories(
      MyBatisLearnerMemoryClaimRepository claims,
      MyBatisLearnerMemoryUpdateRunRepository runs,
      MyBatisLearnerMemoryEvidenceRepository evidence
  ) {
  }
}
