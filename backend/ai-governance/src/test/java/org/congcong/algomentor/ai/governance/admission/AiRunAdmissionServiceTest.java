package org.congcong.algomentor.ai.governance.admission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.identity.model.AuthRole;
import org.junit.jupiter.api.Test;

class AiRunAdmissionServiceTest {

  @Test
  void rejectsDisabledPurposeBeforeAuthenticationCheck() {
    Fixture fixture = new Fixture();
    fixture.properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setEnabled(false);

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(AiActor.anonymous(), AiPurpose.LEARNING_CHAT, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_PURPOSE_DISABLED);
    assertThat(fixture.usage.consumeCalls).isZero();
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsUnauthenticatedActor() {
    Fixture fixture = new Fixture();

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(AiActor.anonymous(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_UNAUTHENTICATED);
  }

  @Test
  void rejectsRequestTooLargeBeforeQuotaAndLock() {
    Fixture fixture = new Fixture();
    fixture.properties.getPurposes().get(AiPurpose.LEARNING_PLAN).setMaxRequestBytes(5);

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 6)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_REQUEST_TOO_LARGE);
    assertThat(fixture.usage.consumeCalls).isZero();
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsQuotaBeforeLock() {
    Fixture fixture = new Fixture();
    fixture.usage.consumeResult = false;

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_QUOTA_EXCEEDED);
    assertThat(fixture.locks.acquireCalls).isZero();
  }

  @Test
  void rejectsConcurrentRunAfterQuotaConsumed() {
    Fixture fixture = new Fixture();
    fixture.locks.result = Optional.empty();

    AiRunAdmissionException ex = catchThrowableOfType(
        () -> fixture.service.admit(fixture.context(fixture.user(), AiPurpose.LEARNING_PLAN, 10)),
        AiRunAdmissionException.class);

    assertThat(ex.code()).isEqualTo(AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT);
    assertThat(fixture.usage.consumeCalls).isEqualTo(1);
  }

  @Test
  void admitsAndReturnsMetadataForAgentRequest() {
    Fixture fixture = new Fixture();

    AiRunAdmission admission = fixture.service.admit(
        fixture.context(fixture.user(), AiPurpose.PROBLEM_EXPLANATION, 10));

    assertThat(admission.metadata())
        .containsEntry(AiGovernanceMetadataKeys.RUN_ID, "run-1")
        .containsEntry(AiGovernanceMetadataKeys.PURPOSE, "PROBLEM_EXPLANATION")
        .containsEntry(AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL");
    assertThat(admission.status()).isEqualTo(AiRunStatus.ADMITTED);
  }

  private static final class Fixture {

    private final AiGovernanceProperties properties = new AiGovernanceProperties();
    private final RecordingUsage usage = new RecordingUsage();
    private final RecordingLocks locks = new RecordingLocks();
    private final RecordingAdmissionRepository repository = new RecordingAdmissionRepository();
    private final AiRunAdmissionService service = new AiRunAdmissionService(
        properties,
        new AiPurposePolicyResolver(properties),
        usage,
        locks,
        repository);

    private AiActor user() {
      return new AiActor(7L, Set.of(AuthRole.USER), true);
    }

    private AiRunContext context(AiActor actor, AiPurpose purpose, int requestSize) {
      return new AiRunContext(
          "run-1",
          actor,
          purpose,
          purpose == AiPurpose.LEARNING_CHAT ? AiRunSource.LEARNING_CHAT : AiRunSource.LEARNING_PLAN_DRAFT,
          null,
          requestSize,
          false,
          Map.of(),
          Instant.parse("2026-06-23T00:00:00Z"));
    }
  }

  private static final class RecordingUsage implements AiDailyUsageStore {

    private int consumeCalls;
    private boolean consumeResult = true;

    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      consumeCalls++;
      return consumeResult;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class RecordingLocks extends AiRunLockService {

    private int acquireCalls;
    private Optional<AgentRunLockToken> result = Optional.of(new AgentRunLockToken(
        "user:7:ai:all", "node-1", "token-1", null));

    private RecordingLocks() {
      super(null, null, null);
    }

    @Override
    public Optional<AgentRunLockToken> tryAcquire(long userId, String runId, Map<String, Object> metadata) {
      acquireCalls++;
      return result;
    }

    @Override
    public void release(AgentRunLockToken token) {
    }
  }

  private static final class RecordingAdmissionRepository extends PostgresAiRunAdmissionRepository {

    private long nextId = 1;

    private RecordingAdmissionRepository() {
      super(null);
    }

    @Override
    public Long insert(AiRunContext context, AiRunStatus status, AiGovernanceErrorCode rejectionCode) {
      return nextId++;
    }

    @Override
    public Long insertRejected(
        String runId,
        Long userId,
        AiPurpose purpose,
        AiRunSource source,
        String idempotencyKey,
        int requestSize,
        AiRunStatus status,
        AiGovernanceErrorCode rejectionCode) {
      return nextId++;
    }
  }
}
