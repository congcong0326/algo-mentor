package org.congcong.algomentor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.ai.governance.accounting.AiAccountingLlmGateway;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallAccountingService;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallContextResolver;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallStatus;
import org.congcong.algomentor.ai.governance.metrics.AiProviderCallMetricsLlmGateway;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.DynamicLlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.junit.jupiter.api.Test;

class AiReasoningEffortEndToEndIT {

  private static final LlmProviderType OPENAI = LlmProviderType.of("openai");
  private static final LlmModelId MODEL = LlmModelId.of("gpt-test");

  @Test
  void keepsLegacyRouteAtProviderDefaultAcrossDispatchAccountingAndMetrics() {
    RecordingProviderClient client = new RecordingProviderClient();
    Pipeline pipeline = new Pipeline(client, supportedCapabilities());

    pipeline.gateway.complete(request(null, pipeline.target(client, null)));

    assertThat(client.completedRequests).singleElement()
        .extracting(request -> request.options().reasoningEffort())
        .isNull();
    assertThat(pipeline.mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isNull();
    assertMetric(pipeline.registry, "provider_default", "success");
  }

  @Test
  void propagatesRouteEffortAcrossDispatchAccountingAndMetrics() {
    RecordingProviderClient client = new RecordingProviderClient();
    Pipeline pipeline = new Pipeline(client, supportedCapabilities());

    pipeline.gateway.complete(request(null, pipeline.target(client, LlmReasoningEffort.HIGH)));

    assertThat(client.completedRequests).singleElement()
        .extracting(request -> request.options().reasoningEffort())
        .isEqualTo(LlmReasoningEffort.HIGH);
    assertThat(pipeline.mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isEqualTo("high");
    assertMetric(pipeline.registry, "high", "success");
  }

  @Test
  void requestEffortOverridesTheRouteForAllThreeRuntimeConsumers() {
    RecordingProviderClient client = new RecordingProviderClient();
    Pipeline pipeline = new Pipeline(client, supportedCapabilities());

    pipeline.gateway.complete(request(LlmReasoningEffort.NONE, pipeline.target(client, LlmReasoningEffort.HIGH)));

    assertThat(client.completedRequests).singleElement()
        .extracting(request -> request.options().reasoningEffort())
        .isEqualTo(LlmReasoningEffort.NONE);
    assertThat(pipeline.mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isEqualTo("none");
    assertMetric(pipeline.registry, "none", "success");
  }

  @Test
  void rejectsAnUnsupportedCapabilityBeforeTheProviderClientIsCalled() {
    RecordingProviderClient client = new RecordingProviderClient();
    Pipeline pipeline = new Pipeline(client, Set.of(LlmCapability.CHAT_COMPLETION));

    assertThatThrownBy(() -> pipeline.gateway.complete(request(null, pipeline.target(client, LlmReasoningEffort.HIGH))))
        .isInstanceOfSatisfying(LlmException.class,
            exception -> assertThat(exception.code()).isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY));

    assertThat(client.completedRequests).isEmpty();
    assertThat(pipeline.mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isEqualTo("high");
    assertMetric(pipeline.registry, "high", "failure");
  }

  @Test
  void rejectsAnEffortOutsideTheAdapterAcceptedSubsetWhenSavingARoute() {
    AiConfiguredModelRepository models = mock(AiConfiguredModelRepository.class);
    AiProviderInstanceRepository providers = mock(AiProviderInstanceRepository.class);
    when(models.findById(101L)).thenReturn(java.util.Optional.of(new AiConfiguredModel(
        101L, 11L, "Test model", "gpt-test", false, Instant.EPOCH, Instant.EPOCH)));
    when(providers.findById(11L)).thenReturn(java.util.Optional.of(new AiProviderInstance(
        11L, "Test provider", "restricted", false, JsonNodeFactory.instance.objectNode(), Instant.EPOCH, Instant.EPOCH)));
    AiProviderManagementService service = new AiProviderManagementService(
        providers,
        models,
        new LlmProviderAdapterRegistry(List.of(new RestrictedEffortAdapter())),
        Clock.systemUTC());

    assertThatThrownBy(() -> service.validateModelRoute(101L, LlmReasoningEffort.HIGH))
        .isInstanceOfSatisfying(AiGovernanceAdminException.class,
            exception -> assertThat(exception.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_INVALID));
  }

  @Test
  void keepsTheBoundTargetWhenTheRouteChangesDuringTheSameRun() {
    RecordingProviderClient client = new RecordingProviderClient();
    Pipeline pipeline = new Pipeline(client, supportedCapabilities());
    AiRunInvocationTargetStore targets = new AiRunInvocationTargetStore();
    ResolvedAiModelSnapshot beforeRouteChange = snapshot(client, LlmReasoningEffort.HIGH);
    ResolvedAiModelSnapshot afterRouteChange = snapshot(client, LlmReasoningEffort.NONE);
    targets.bind("run-snapshot", beforeRouteChange.invocationTarget());

    assertThat(afterRouteChange.invocationTarget().routeReasoningEffort()).isEqualTo(LlmReasoningEffort.NONE);
    LlmInvocationTarget boundTarget = targets.find("run-snapshot").orElseThrow();
    pipeline.gateway.complete(request(null, boundTarget));

    assertThat(client.completedRequests).singleElement()
        .extracting(request -> request.options().reasoningEffort())
        .isEqualTo(LlmReasoningEffort.HIGH);
  }

  @Test
  void streamCancellationKeepsTheStartEffortAndCancelsTheProviderSubscription() {
    PendingProviderClient client = new PendingProviderClient();
    Pipeline pipeline = new Pipeline(client, supportedCapabilities());
    AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    pipeline.gateway.stream(request(null, pipeline.target(client, LlmReasoningEffort.HIGH)))
        .subscribe(new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription value) {
            subscription.set(value);
          }

          @Override
          public void onNext(LlmStreamEvent item) {
          }

          @Override
          public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
          }

          @Override
          public void onComplete() {
          }
        });
    subscription.get().cancel();

    assertThat(client.cancelled).isTrue();
    assertThat(pipeline.mapper.rows).singleElement()
        .extracting(AiLlmCallUsageRow::reasoningEffort)
        .isEqualTo("high");
    assertThat(pipeline.mapper.updates).singleElement()
        .extracting(AiLlmCallUsageUpdate::status)
        .isEqualTo(AiLlmCallStatus.CANCELLED);
    assertMetric(pipeline.registry, "high", "cancelled");
  }

  private static void assertMetric(SimpleMeterRegistry registry, String reasoningEffort, String status) {
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "reasoning_effort", reasoningEffort, "status", status)
        .counter().count()).isEqualTo(1d);
  }

  private static LlmCompletionRequest request(LlmReasoningEffort requestEffort, LlmInvocationTarget target) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of()))
        .messages(List.of(LlmMessage.user("hello")))
        .metadata(Map.of(
            AiGovernanceMetadataKeys.RUN_ID, "run-1",
            AiGovernanceMetadataKeys.USER_ID, 7L,
            AiGovernanceMetadataKeys.PURPOSE, "LEARNING_CHAT",
            AiGovernanceMetadataKeys.SOURCE, "PRACTICE_CHAT",
            AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL"))
        .options(LlmGenerationOptions.defaults().withReasoningEffort(requestEffort))
        .invocationTarget(target)
        .build();
  }

  private static ResolvedAiModelSnapshot snapshot(LlmProviderClient client, LlmReasoningEffort effort) {
    return new ResolvedAiModelSnapshot(
        org.congcong.algomentor.ai.governance.model.AiBusinessScenario.PRACTICE_CHAT,
        17L,
        2L,
        PolicyMatchSource.GROUP,
        9L,
        101L,
        MODEL.value(),
        11L,
        OPENAI.value(),
        Instant.parse("2026-08-03T00:00:00Z"),
        client,
        supportedCapabilities(),
        effort);
  }

  private static Set<LlmCapability> supportedCapabilities() {
    return Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.STREAMING, LlmCapability.REASONING_EFFORT);
  }

  private static LlmCompletionResult result() {
    return new LlmCompletionResult(
        LlmMessage.assistant("ok"),
        List.of(),
        JsonNodeFactory.instance.nullNode(),
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of("openai"),
        MODEL,
        Map.of());
  }

  private static final class Pipeline {

    private final RecordingMapper mapper = new RecordingMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LlmGateway gateway;
    private final Set<LlmCapability> capabilities;

    private Pipeline(LlmProviderClient client, Set<LlmCapability> capabilities) {
      this.capabilities = capabilities;
      AiLlmCallAccountingService accounting = new AiLlmCallAccountingService(
          mapper,
          new NoopUsageStore(),
          new AiLlmCallContextResolver(),
          Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC),
          ZoneOffset.UTC,
          null);
      gateway = new AiAccountingLlmGateway(
          new AiProviderCallMetricsLlmGateway(new DynamicLlmGateway(), registry), accounting);
    }

    private LlmInvocationTarget target(LlmProviderClient client, LlmReasoningEffort routeEffort) {
      return new LlmInvocationTarget(
          OPENAI,
          11L,
          101L,
          MODEL,
          Instant.parse("2026-08-03T00:00:00Z"),
          capabilities,
          client,
          routeEffort);
    }
  }

  private static class RecordingProviderClient implements LlmProviderClient {

    private final List<LlmCompletionRequest> completedRequests = new ArrayList<>();

    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      completedRequests.add(request);
      return result();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class PendingProviderClient extends RecordingProviderClient {

    private boolean cancelled;

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
          cancelled = true;
        }
      });
    }
  }

  private static final class RecordingMapper implements AiLlmCallUsageMapper {

    private final List<AiLlmCallUsageRow> rows = new ArrayList<>();
    private final List<AiLlmCallUsageUpdate> updates = new ArrayList<>();

    @Override
    public int insert(AiLlmCallUsageRow row) {
      rows.add(row);
      return 1;
    }

    @Override
    public int updateTerminal(AiLlmCallUsageUpdate update) {
      updates.add(update);
      return 1;
    }
  }

  private static final class NoopUsageStore implements AiDailyUsageStore {

    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      return true;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class RestrictedEffortAdapter implements LlmProviderAdapter {

    @Override
    public LlmProviderType providerType() {
      return LlmProviderType.of("restricted");
    }

    @Override
    public String displayName() {
      return "Restricted";
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.REASONING_EFFORT);
    }

    @Override
    public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
      return Set.of(LlmReasoningEffort.NONE, LlmReasoningEffort.LOW);
    }

    @Override
    public void validateConfig(JsonNode config) {
    }

    @Override
    public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
      throw new UnsupportedOperationException();
    }
  }
}
