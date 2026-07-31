package org.congcong.algomentor.ai.governance.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.junit.jupiter.api.Test;

class AiRunGovernanceServiceTest {

  @Test
  void userEntryConsumesSharedQuotaAcquiresLockAndReleasesEveryResourceOnCompletion() {
    Fixture fixture = new Fixture();

    AiRunGovernanceLease lease = fixture.service.begin(request(
        AiRunGovernanceMode.USER_ENTRY,
        "run-user-1",
        AiPurpose.LEARNING_CHAT,
        AiRunSource.PRACTICE_CHAT));

    assertThat(fixture.usage.consumeCalls).isEqualTo(1);
    assertThat(fixture.locks.acquireCalls).isEqualTo(1);
    assertThat(fixture.routes.lastScenario).isEqualTo(AiBusinessScenario.PRACTICE_CHAT);
    assertThat(lease.metadata())
        .containsEntry(AiGovernanceMetadataKeys.CALL_KIND, AiLlmCallKind.AGENT_STEP.name())
        .containsEntry(AiGovernanceMetadataKeys.SCENARIO_CODE, "practice-chat");
    assertThat(fixture.targets.find("run-user-1")).contains(lease.invocationTarget());

    lease.complete(AiUsage.zero(), "openai", "gpt-test");
    lease.complete(AiUsage.zero(), "openai", "gpt-test");

    assertThat(fixture.repository.lastStatus).isEqualTo(AiRunStatus.COMPLETED);
    assertThat(fixture.locks.releaseCalls).isEqualTo(1);
    assertThat(fixture.targets.find("run-user-1")).isEmpty();
  }

  @Test
  void childChecksDynamicPolicyRoutesItsOwnScenarioAndDoesNotTouchAdmissionResources() {
    Fixture fixture = new Fixture();

    AiRunGovernanceLease lease = fixture.service.begin(request(
        AiRunGovernanceMode.CHILD,
        "run-child-1",
        AiPurpose.LEARNING_CHAT,
        AiRunSource.PRACTICE_CHAT));

    assertThat(fixture.usage.consumeCalls).isZero();
    assertThat(fixture.locks.acquireCalls).isZero();
    assertThat(fixture.routes.lastScenario).isEqualTo(AiBusinessScenario.PRACTICE_CHAT);
    assertThat(lease.admission()).isEmpty();
    assertThat(lease.metadata()).containsEntry(AiGovernanceMetadataKeys.CALL_KIND, AiLlmCallKind.AGENT_STEP.name());
    assertThat(fixture.targets.find("run-child-1")).contains(lease.invocationTarget());

    lease.fail(AiGovernanceErrorCode.AI_TIMEOUT, AiUsage.zero(), "openai", "gpt-test");
    lease.cancel(AiUsage.zero(), "openai", "gpt-test");

    assertThat(fixture.repository.lastStatus).isNull();
    assertThat(fixture.locks.releaseCalls).isZero();
    assertThat(fixture.targets.find("run-child-1")).isEmpty();
  }

  private static AiRunGovernanceRequest request(
      AiRunGovernanceMode mode,
      String runId,
      AiPurpose purpose,
      AiRunSource source
  ) {
    return new AiRunGovernanceRequest(
        mode,
        runId,
        7L,
        purpose,
        source,
        "idem-" + runId,
        64,
        false,
        AiLlmCallKind.AGENT_STEP,
        "ALL",
        Map.of("callerMetadata", "trusted"));
  }

  private static final class Fixture {

    private final AiGovernanceProperties properties = new AiGovernanceProperties();
    private final AiPurposePolicyResolver policyResolver = new AiPurposePolicyResolver(properties);
    private final RecordingUsage usage = new RecordingUsage();
    private final RecordingLocks locks = new RecordingLocks();
    private final RecordingAdmissionRepository repository = new RecordingAdmissionRepository();
    private final RecordingRoutes routes = new RecordingRoutes();
    private final AiRunInvocationTargetStore targets = new AiRunInvocationTargetStore();
    private final AiRunAdmissionService admission = new AiRunAdmissionService(
        properties, policyResolver, usage, locks, repository, null, routes, targets);
    private final AiRunLifecycleService lifecycle = new AiRunLifecycleService(
        properties, repository, usage, locks, targets);
    private final AiRunGovernanceService service = new AiRunGovernanceService(
        admission, lifecycle, policyResolver, enabledRuntimePolicy(), routes, targets);
  }

  private static AiRuntimePolicyService enabledRuntimePolicy() {
    return new AiRuntimePolicyService(null, null, null) {
      @Override
      public EffectiveAiRuntimePolicy resolve(AiPurposePolicy staticPolicy, long userId) {
        return new EffectiveAiRuntimePolicy(true, null, true, null, 10, null, 10, null, null);
      }
    };
  }

  private static final class RecordingUsage implements AiDailyUsageStore {

    private int consumeCalls;

    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      consumeCalls++;
      return true;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class RecordingLocks extends AiRunLockService {

    private int acquireCalls;
    private int releaseCalls;

    private RecordingLocks() {
      super(null, null, null);
    }

    @Override
    public Optional<AgentRunLockToken> tryAcquire(long userId, String runId, Map<String, Object> metadata) {
      acquireCalls++;
      return Optional.of(new AgentRunLockToken("user:7:ai:all", "node-1", "token-1", null));
    }

    @Override
    public void release(AgentRunLockToken token) {
      releaseCalls++;
    }
  }

  private static final class RecordingAdmissionRepository extends PostgresAiRunAdmissionRepository {

    private AiRunStatus lastStatus;

    private RecordingAdmissionRepository() {
      super(null);
    }

    @Override
    public Long insert(AiRunContext context, AiRunStatus status, AiGovernanceErrorCode rejectionCode) {
      return 1L;
    }

    @Override
    public void updateStatus(
        Long admissionId,
        String runId,
        AiRunStatus status,
        AiGovernanceErrorCode errorCode,
        AiUsage usage,
        String provider,
        String model,
        Instant completedAt) {
      lastStatus = status;
    }
  }

  private static final class RecordingRoutes implements AiModelRouteResolver {

    private AiBusinessScenario lastScenario;

    @Override
    public ResolvedAiModelSnapshot resolve(AiBusinessScenario scenario, long userId) {
      lastScenario = scenario;
      return new ResolvedAiModelSnapshot(
          scenario,
          17L,
          2L,
          PolicyMatchSource.GROUP,
          9L,
          101L,
          "gpt-test",
          11L,
          "openai",
          Instant.parse("2026-07-27T00:00:00Z"),
          new LlmProviderClient() {
            @Override
            public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
              throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.Flow.Publisher<LlmStreamEvent> stream(
                LlmModelId upstreamModelId,
                LlmCompletionRequest request) {
              throw new UnsupportedOperationException();
            }
          },
          Set.of(LlmCapability.CHAT_COMPLETION));
    }
  }
}
