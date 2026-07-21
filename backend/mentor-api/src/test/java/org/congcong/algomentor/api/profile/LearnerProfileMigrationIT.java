package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryDraft;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryStatus;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.junit.jupiter.api.Test;

class LearnerProfileMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void enforcesProfileIdentityConstraintsAndReadsCurrentHistory() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MyBatisLearnerProfileRepository repository = repository();
    LearnerProfileIdentity identity = LearnerProfileIdentity.dimension(
        userId, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.LEARNER_BACKGROUND);

    var entry = repository.insert(new LearnerProfileEntryDraft(
        identity, 1, LearnerProfileEntryStatus.ACTIVE, "Has Java experience", null,
        LearnerProfileOriginType.USER_EXPLICIT, null, null, null, Instant.now(), null));

    assertThat(repository.findCurrent(identity)).contains(entry);
    assertThat(repository.findHistory(identity)).containsExactly(entry);
    assertThatThrownBy(() -> execute(
        """
        INSERT INTO learner_profile_entry (user_id, entry_kind, dimension, revision_no, status, content_text, origin_type, valid_from)
        VALUES (?, 'DECLARED_FACT', 'LEARNER_BACKGROUND', 2, 'ACTIVE', 'duplicate', 'USER_EXPLICIT', NOW())
        """,
        userId)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> execute(
        """
        INSERT INTO learner_profile_entry (user_id, entry_kind, dimension, revision_no, status, content_text, origin_type, valid_from)
        VALUES (?, 'TAG_ASSESSMENT', 'LEARNER_BACKGROUND', 1, 'ACTIVE', 'invalid scope', 'SYSTEM_DERIVED', NOW())
        """,
        userId)).isInstanceOf(Exception.class);

    execute("DELETE FROM auth_users WHERE id = ?", userId);
    assertThat(count("learner_profile_entry")).isZero();
  }

  private MyBatisLearnerProfileRepository repository() throws Exception {
    return new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
  }
}
