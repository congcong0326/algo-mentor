package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
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
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.junit.jupiter.api.Test;

class LearnerMemoryRecallIT extends PostgresIntegrationTestSupport {

  private final LearnerMemoryClaimTextHasher textHasher = new LearnerMemoryClaimTextHasher();

  @Test
  void opensAUserBoundSnapshotAndSelectsCurrentProblemClaimsFromV46Tables() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    insertProblem("current-problem", 1, List.of(), List.of(), List.of());
    long currentTagId = insertCatalog("array", "Array", "数组", true);
    long otherTagId = insertCatalog("two-pointers", "Two Pointers", "双指针", true);
    assignTag("current-problem", currentTagId, 0);
    MemoryRepositories repositories = repositories();
    insertClaim(repositories, userId, LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, null, "准备后端面试",
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);
    insertClaim(repositories, userId, LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.IMPLEMENTATION_AND_ERROR_PATTERN, null, "实现时经常遗漏边界条件",
        LearnerMemoryEvidenceContract.Grade.STRONG);
    insertClaim(repositories, userId, LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY, currentTagId, "数组题双指针较稳定",
        LearnerMemoryEvidenceContract.Grade.SUPPORTED);
    insertClaim(repositories, userId, LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY, otherTagId, "双指针仍需复盘",
        LearnerMemoryEvidenceContract.Grade.SUPPORTED);
    insertClaim(repositories, otherUserId, LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, null, "其他用户的目标",
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);

    LearnerMemoryRecallService.OpenedSnapshot opened = recallService(repositories).openSnapshot(
        userId, "实现时经常遗漏边界条件，给我一个提示", "current-problem", "zh-CN");
    LearnerMemoryRecallSnapshot snapshot = opened.snapshot();

    assertThat(snapshot.documentRevision()).hasSize(64);
    assertThat(snapshot.claimCount()).isEqualTo(4);
    assertThat(snapshot.currentProblemMatchCount()).isEqualTo(1);
    assertThat(snapshot.directHits()).extracting(hit -> hit.claim().claimText())
        .containsExactly("准备后端面试", "数组题双指针较稳定", "实现时经常遗漏边界条件");
    assertThat(snapshot.directHits()).extracting(LearnerMemoryRecallSnapshot.Statement::sourceSummary)
        .containsExactly("用户明确自述", "当前题目标签：数组", "正式代码复盘");
    assertThat(snapshot.sections()).flatExtracting(LearnerMemoryRecallSnapshot.Section::statements)
        .extracting(statement -> statement.claim().claimText())
        .doesNotContain("其他用户的目标");
    assertThat(snapshot.scopeRef()).isNotEqualTo(Long.toString(userId));

    opened.lease().release();
  }

  private LearnerMemoryRecallService recallService(MemoryRepositories repositories) {
    return new LearnerMemoryRecallService(
        new LearnerMemoryClaimQueryService(repositories.claims(), new LearnerMemoryClaimSnapshotFactory()),
        repositories.tags(),
        new LearnerMemorySectionCatalog(),
        new LearnerMemoryDirectHitSelector(),
        new LearnerMemoryRunScopeRegistry());
  }

  private MemoryRepositories repositories() throws Exception {
    var sessionTemplate = sqlSessionTemplate(
        "mapper/profile/LearnerMemoryMapper.xml",
        "mapper/problem/ProblemTagMapper.xml");
    LearnerMemoryMapper memoryMapper = sessionTemplate.getMapper(LearnerMemoryMapper.class);
    return new MemoryRepositories(
        new MyBatisLearnerMemoryClaimRepository(memoryMapper),
        new MyBatisLearnerMemoryUpdateRunRepository(memoryMapper),
        new MyBatisTrustedProblemTagCatalog(sessionTemplate.getMapper(ProblemTagMapper.class)));
  }

  private LearnerMemoryClaimRevision insertClaim(
      MemoryRepositories repositories,
      long userId,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      Long tagId,
      String text,
      LearnerMemoryEvidenceContract.Grade grade
  ) {
    LearnerMemoryUpdateRun run = repositories.runs().create(new LearnerMemoryUpdateRunDraft(
        userId,
        kind == LearnerMemoryClaimContract.Kind.DECLARED_FACT
            ? LearnerMemoryRunContract.Trigger.DECLARED_FACT
            : LearnerMemoryRunContract.Trigger.CODE_REVIEW_BATCH,
        "recall-it-" + UUID.randomUUID(),
        "v1",
        "v1",
        1,
        Instant.now()));
    LearnerMemoryEvidenceContract.Pattern pattern = kind == LearnerMemoryClaimContract.Kind.DECLARED_FACT
        ? LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION
        : kind == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT
            ? LearnerMemoryEvidenceContract.Pattern.TAG_BREADTH
            : LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE;
    return repositories.claims().insert(new LearnerMemoryClaimRevisionDraft(
        UUID.randomUUID(),
        userId,
        new LearnerMemoryClaimScope(kind, dimension, tagId),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        textHasher.hash(text),
        kind == LearnerMemoryClaimContract.Kind.DECLARED_FACT
            ? LearnerMemoryClaimContract.Origin.USER_EXPLICIT
            : LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        pattern,
        grade,
        null,
        run.id(),
        null,
        Instant.now(),
        null));
  }

  private record MemoryRepositories(
      MyBatisLearnerMemoryClaimRepository claims,
      MyBatisLearnerMemoryUpdateRunRepository runs,
      TrustedProblemTagCatalog tags) {
  }
}
