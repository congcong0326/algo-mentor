package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfilePolicyResolver;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfileRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfileRecallSnapshot;
import org.junit.jupiter.api.Test;

class LearnerProfileRecallIT extends PostgresIntegrationTestSupport {

  @Test
  void recallsOnlyActiveEntriesForTheUserAndCurrentProblemTrustedTags() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    insertProblem("current-problem", 1, List.of(), List.of(), List.of());
    insertProblem("other-problem", 2, List.of(), List.of(), List.of());
    long currentTagId = insertCatalog("hash-table", "Hash Table", "哈希表", true);
    long otherTagId = insertCatalog("two-pointers", "Two Pointers", "双指针", true);
    assignTag("current-problem", currentTagId, 0);
    assignTag("other-problem", otherTagId, 0);
    insertActive(userId, "DECLARED_FACT", "GOALS_AND_INTENTS", null, "current-user-goal");
    insertActive(userId, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null, "current-user-approach");
    insertActive(userId, "TAG_ASSESSMENT", "TAG_MASTERY", currentTagId, "current-tag-assessment");
    insertActive(userId, "TAG_ASSESSMENT", "TAG_MASTERY", otherTagId, "other-problem-tag-must-not-recall");
    insertSuppressed(userId, "DECLARED_FACT", "SELF_ABILITY_ASSESSMENT", null, "suppressed-must-not-recall");
    insertActive(otherUserId, "DECLARED_FACT", "GOALS_AND_INTENTS", null, "other-user-must-not-recall");

    LearnerProfileRecallSnapshot snapshot = recallService().recall(
        userId, PracticeChatPromptConstants.SCENARIO, "current-problem");

    assertThat(snapshot.declaredFacts()).extracting(LearnerProfileEntry::contentText)
        .containsExactly("current-user-goal");
    assertThat(snapshot.generalObservations()).extracting(LearnerProfileEntry::contentText)
        .containsExactly("current-user-approach");
    assertThat(snapshot.currentProblemTagAssessments())
        .extracting(assessment -> assessment.tag().tagId())
        .containsExactly(currentTagId);
    assertThat(snapshot.currentProblemTagAssessments())
        .extracting(assessment -> assessment.entry().contentText())
        .containsExactly("current-tag-assessment");
  }

  private LearnerProfileRecallService recallService() throws Exception {
    var sessionTemplate = sqlSessionTemplate(
        "mapper/profile/LearnerProfileMapper.xml",
        "mapper/problem/ProblemTagMapper.xml");
    LearnerProfileQueryService queryService = new LearnerProfileQueryService(
        new MyBatisLearnerProfileRepository(sessionTemplate.getMapper(LearnerProfileMapper.class)));
    return new LearnerProfileRecallService(
        new LearnerProfilePolicyResolver(true, 800),
        queryService,
        new MyBatisTrustedProblemTagCatalog(sessionTemplate.getMapper(ProblemTagMapper.class)));
  }

  private void insertActive(long userId, String kind, String dimension, Long tagId, String content) throws Exception {
    execute(
        """
        INSERT INTO learner_profile_entry (
          user_id, entry_kind, dimension, tag_id, revision_no, status, content_text, origin_type, valid_from
        ) VALUES (?, ?, ?, ?, 1, 'ACTIVE', ?, 'SYSTEM_DERIVED', NOW())
        """,
        userId, kind, dimension, tagId, content);
  }

  private void insertSuppressed(long userId, String kind, String dimension, Long tagId, String content) throws Exception {
    execute(
        """
        INSERT INTO learner_profile_entry (
          user_id, entry_kind, dimension, tag_id, revision_no, status, content_text, origin_type, valid_from, valid_to
        ) VALUES (?, ?, ?, ?, 1, 'SUPPRESSED', ?, 'SYSTEM_DERIVED', NOW(), NOW())
        """,
        userId, kind, dimension, tagId, content);
  }
}
