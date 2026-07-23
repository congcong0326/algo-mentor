package org.congcong.algomentor.auth.session.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionListQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionActivity;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;
import org.congcong.algomentor.auth.session.admin.repository.AuthSessionAdminRepository;
import org.congcong.algomentor.common.admin.audit.AdminAuditOutcome;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;

class AuthSessionAdminServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T09:26:00Z");
  private static final String SESSION_REF = "0f5cdb19-97f8-4e52-9ca8-218bfa8b3d44";
  private static final String TARGET_SESSION_ID = "session-target";

  @Test
  void listsWithOneClockSnapshotAndMarksCurrentSession() {
    FakeRepository repository = new FakeRepository();
    repository.records = List.of(record("session-current", NOW.minus(Duration.ofMinutes(5))));
    repository.summary = new AuthSessionAdminSummary(1, 1, 1);
    AuthSessionAdminService service = service(repository, new FakeRevocationService(), new CapturingAuditRecorder());

    var result = service.list(new AdminAuthSessionListQuery(0, 200, " 42 ", "ACTIVE"), "session-current");

    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(100);
    assertThat(result.checkedAt()).isEqualTo(NOW);
    assertThat(repository.activeSince).isEqualTo(NOW.minus(Duration.ofMinutes(5)));
    assertThat(repository.query.keywordUserId()).isEqualTo(42L);
    assertThat(repository.query.activity().name()).isEqualTo("ACTIVE");
    assertThat(result.items()).singleElement().satisfies(item -> {
      assertThat(item.current()).isTrue();
      assertThat(item.activity()).isEqualTo(AuthSessionActivity.ACTIVE);
    });
  }

  @Test
  void revokesOtherSessionAndRecordsLowSensitivityAudit() {
    FakeRepository repository = new FakeRepository();
    repository.target = record(TARGET_SESSION_ID, NOW.minusSeconds(30));
    FakeRevocationService revocationService = new FakeRevocationService();
    CapturingAuditRecorder audit = new CapturingAuditRecorder();
    AuthSessionAdminService service = service(repository, revocationService, audit);

    var result = service.revoke(SESSION_REF, "session-current", 7L);

    assertThat(result.revoked()).isTrue();
    assertThat(result.alreadyOffline()).isFalse();
    assertThat(result.userId()).isEqualTo(42L);
    assertThat(revocationService.revokedSessionIds).containsExactly(TARGET_SESSION_ID);
    assertThat(audit.events).singleElement().satisfies(event -> {
      assertThat(event.outcome()).isEqualTo(AdminAuditOutcome.SUCCESS);
      assertThat(event.targetRef()).isEqualTo(SESSION_REF);
      assertThat(event.metadata().values()).doesNotContain(TARGET_SESSION_ID);
    });
  }

  @Test
  void rejectsCurrentSessionWithoutCallingDeletion() {
    FakeRepository repository = new FakeRepository();
    repository.target = record("session-current", NOW.minusSeconds(30));
    FakeRevocationService revocationService = new FakeRevocationService();
    CapturingAuditRecorder audit = new CapturingAuditRecorder();
    AuthSessionAdminService service = service(repository, revocationService, audit);

    assertThatThrownBy(() -> service.revoke(SESSION_REF, "session-current", 7L))
        .isInstanceOf(AuthSessionAdminException.class)
        .extracting(exception -> ((AuthSessionAdminException) exception).code())
        .isEqualTo(AuthSessionAdminErrorCode.AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN);
    assertThat(revocationService.revokedSessionIds).isEmpty();
    assertThat(audit.events).singleElement().extracting(AdminOperationAuditEvent::outcome)
        .isEqualTo(AdminAuditOutcome.FAILURE);
  }

  @Test
  void treatsMissingAndRacedDeletionAsAlreadyOffline() {
    FakeRepository missingRepository = new FakeRepository();
    CapturingAuditRecorder missingAudit = new CapturingAuditRecorder();
    AuthSessionAdminService missingService = service(
        missingRepository,
        new FakeRevocationService(),
        missingAudit);

    var missing = missingService.revoke(SESSION_REF, "session-current", 7L);

    assertThat(missing.alreadyOffline()).isTrue();
    assertThat(missing.userId()).isNull();
    assertThat(missingAudit.events).singleElement().extracting(AdminOperationAuditEvent::outcome)
        .isEqualTo(AdminAuditOutcome.SUCCESS);

    FakeRepository racedRepository = new FakeRepository();
    racedRepository.target = record(TARGET_SESSION_ID, NOW.minusSeconds(30));
    FakeRevocationService racedRevocation = new FakeRevocationService();
    racedRevocation.nextResult = false;
    var raced = service(racedRepository, racedRevocation, new CapturingAuditRecorder())
        .revoke(SESSION_REF, "session-current", 7L);

    assertThat(raced.revoked()).isFalse();
    assertThat(raced.alreadyOffline()).isTrue();
    assertThat(raced.userId()).isEqualTo(42L);
  }

  private static AuthSessionAdminService service(
      FakeRepository repository,
      FakeRevocationService revocationService,
      CapturingAuditRecorder auditRecorder
  ) {
    AuthProperties properties = new AuthProperties();
    properties.setSessionMonitoringActiveWindow(Duration.ofMinutes(5));
    return new AuthSessionAdminService(
        repository,
        revocationService,
        auditRecorder,
        new NoopAuthSessionAdminMetrics(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        properties);
  }

  private static AuthSessionAdminRecord record(String sessionId, Instant lastAccessedAt) {
    return new AuthSessionAdminRecord(
        SESSION_REF,
        sessionId,
        42L,
        "user@example.com",
        "Example User",
        AuthUserStatus.ACTIVE,
        NOW.minus(Duration.ofHours(1)),
        lastAccessedAt,
        NOW.plus(Duration.ofDays(7)),
        AuthSessionActivity.ACTIVE,
        false);
  }

  private static final class FakeRepository implements AuthSessionAdminRepository {

    private AuthSessionAdminQuery query;
    private Instant activeSince;
    private List<AuthSessionAdminRecord> records = List.of();
    private AuthSessionAdminSummary summary = new AuthSessionAdminSummary(0, 0, 0);
    private AuthSessionAdminRecord target;

    @Override
    public List<AuthSessionAdminRecord> findPage(
        AuthSessionAdminQuery query,
        Instant checkedAt,
        Instant activeSince
    ) {
      this.query = query;
      this.activeSince = activeSince;
      return records;
    }

    @Override
    public long count(AuthSessionAdminQuery query, Instant checkedAt, Instant activeSince) {
      return records.size();
    }

    @Override
    public AuthSessionAdminSummary summary(Instant checkedAt, Instant activeSince) {
      return summary;
    }

    @Override
    public Optional<AuthSessionAdminRecord> findValidBySessionRef(String sessionRef, Instant checkedAt) {
      return Optional.ofNullable(target);
    }
  }

  private static final class FakeRevocationService implements AuthSessionRevocationService {

    private final List<String> revokedSessionIds = new ArrayList<>();
    private boolean nextResult = true;

    @Override
    public boolean revokeSession(String sessionId) {
      revokedSessionIds.add(sessionId);
      return nextResult;
    }

    @Override
    public int revokeSessionsForUser(long userId) {
      return 0;
    }
  }

  private static final class CapturingAuditRecorder implements AdminOperationAuditRecorder {

    private final List<AdminOperationAuditEvent> events = new ArrayList<>();

    @Override
    public void record(AdminOperationAuditEvent event) {
      events.add(event);
    }
  }
}
