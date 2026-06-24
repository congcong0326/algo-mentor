package org.congcong.algomentor.api.practice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.junit.jupiter.api.Test;

class MyBatisPracticeSessionRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");
  private static final Instant LAST_MESSAGE_AT = Instant.parse("2026-01-02T01:00:00Z");

  @Test
  void mybatisLoadsPracticeSessionMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/practice/PracticeSessionMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/practice/PracticeSessionMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper.";
    assertThat(configuration.hasStatement(namespace + "upsertProgress")).isTrue();
    assertThat(configuration.hasStatement(namespace + "upsertSession")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findSessionByIdForUser")).isTrue();
    assertThat(configuration.hasStatement(namespace + "attachAgentTask")).isTrue();
    assertThat(configuration.hasStatement(namespace + "attachProblemStatementMessage")).isTrue();
    assertThat(configuration.hasStatement(namespace + "updateProgressStatus")).isTrue();
    assertThat(configuration.hasStatement(namespace + "touchLastMessageAt")).isTrue();
  }

  @Test
  void mapsSessionRowToDomainWithPersistedLocale() {
    PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
    when(mapper.upsertSession(7, 12, 1, "two-sum", "en-US")).thenReturn(sessionRow("en-US"));
    MyBatisPracticeSessionRepository repository = new MyBatisPracticeSessionRepository(mapper);

    PracticeSession session = repository.upsertAndLockSession(7, 12, 1, "two-sum", "en-US");

    assertThat(session.id()).isEqualTo(50);
    assertThat(session.status()).isEqualTo(PracticeSessionStatus.ACTIVE);
    assertThat(session.progressStatus()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
    assertThat(session.agentTaskId()).isEqualTo(101);
    assertThat(session.problemStatementMessageId()).isEqualTo(202);
    assertThat(session.lastMessageAt()).isEqualTo(LAST_MESSAGE_AT);
    assertThat(session.locale()).isEqualTo("en-US");
  }

  @Test
  void findSessionForUserReturnsOptional() {
    PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
    when(mapper.findSessionByIdForUser(50, 7)).thenReturn(null);
    MyBatisPracticeSessionRepository repository = new MyBatisPracticeSessionRepository(mapper);

    Optional<PracticeSession> session = repository.findSessionForUser(50, 7);

    assertThat(session).isEmpty();
  }

  @Test
  void mapsProgressStatusStringsToDomainEnum() {
    PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
    when(mapper.updateProgressStatus(50, 7, "SKIPPED")).thenReturn(new PracticeProgressRow(
        70,
        7,
        12,
        1,
        "two-sum",
        "SKIPPED",
        CREATED_AT,
        UPDATED_AT));
    MyBatisPracticeSessionRepository repository = new MyBatisPracticeSessionRepository(mapper);

    PracticeProgress progress = repository.updateProgressStatus(50, 7, PracticeProgressStatus.SKIPPED);

    assertThat(progress.status()).isEqualTo(PracticeProgressStatus.SKIPPED);
    assertThat(progress.problemSlug()).isEqualTo("two-sum");
  }

  private PracticeSessionRow sessionRow(String locale) {
    return new PracticeSessionRow(
        50,
        7,
        12,
        1,
        "two-sum",
        "ACTIVE",
        101L,
        202L,
        "IN_PROGRESS",
        LAST_MESSAGE_AT,
        CREATED_AT,
        UPDATED_AT,
        locale);
  }
}
