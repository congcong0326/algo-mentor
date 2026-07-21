package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearnerProfileViewIT extends PostgresIntegrationTestSupport {

  @Test
  void displayQueryReturnsOnlyCurrentUsersActiveEntriesWithTagLabels() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    long tagId = insertCatalog("binary-search", "Binary Search", "二分查找", true);

    insertEntry(userId, "DECLARED_FACT", "GOALS_AND_INTENTS", null, "准备后端面试。", "ACTIVE");
    insertEntry(userId, "TAG_ASSESSMENT", "TAG_MASTERY", tagId, "循环不变量仍需巩固。", "ACTIVE");
    insertEntry(userId, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null, "不应展示。", "SUPPRESSED");
    insertEntry(otherUserId, "DECLARED_FACT", "GOALS_AND_INTENTS", null, "其他用户内容。", "ACTIVE");

    LearnerProfileMapper mapper = sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml")
        .getMapper(LearnerProfileMapper.class);
    var rows = mapper.findCurrentForDisplay(userId);

    assertThat(rows).extracting(row -> row.contentText())
        .containsExactly("准备后端面试。", "循环不变量仍需巩固。");
    assertThat(rows.get(1).tagId()).isEqualTo(tagId);
    assertThat(rows.get(1).tagValue()).isEqualTo("binary-search");
    assertThat(rows.get(1).tagLabelEn()).isEqualTo("Binary Search");
    assertThat(rows.get(1).tagLabelZh()).isEqualTo("二分查找");
  }

  private void insertEntry(
      long userId,
      String entryKind,
      String dimension,
      Long tagId,
      String content,
      String status
  ) throws Exception {
    execute(
        """
        INSERT INTO learner_profile_entry (
          user_id,
          entry_kind,
          dimension,
          tag_id,
          revision_no,
          status,
          content_text,
          origin_type,
          valid_from,
          valid_to
        ) VALUES (?, ?, ?, ?, 1, ?, ?, 'SYSTEM_DERIVED', NOW(), CASE WHEN ? = 'ACTIVE' THEN NULL ELSE NOW() END)
        """,
        userId,
        entryKind,
        dimension,
        tagId,
        status,
        content,
        status);
  }
}
