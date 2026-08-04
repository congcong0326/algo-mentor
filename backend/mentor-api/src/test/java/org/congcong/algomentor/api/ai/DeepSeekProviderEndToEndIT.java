package org.congcong.algomentor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ToolChoiceOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.congcong.algomentor.ai.governance.accounting.AiAccountingLlmGateway;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallAccountingService;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallContextResolver;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallStatus;
import org.congcong.algomentor.ai.governance.metrics.AiProviderCallMetricsLlmGateway;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.runtime.ProviderClientRegistry;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiLlmCallUsageMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteException;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyContent;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyTypeContributor;
import org.congcong.algomentor.ai.governance.routing.DefaultAiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.llm.core.gateway.DynamicLlmGateway;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.llm.deepseek.DeepSeekProviderAdapter;
import org.congcong.algomentor.llm.deepseek.DeepSeekProviderProfile;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleResponsesClient;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

/** 仅使用内存 fake Responses transport，覆盖 DeepSeek 路由到网关、台账和指标的完整本地链路。 */
class DeepSeekProviderEndToEndIT {

  private static final LlmModelId MODEL_ID = LlmModelId.of("deepseek-local-model");
  private static final Instant VERSION = Instant.parse("2026-08-03T00:00:00Z");

  @Test
  void routesDeepSeekStructuredRequestThroughSnapshotGatewayAccountingAndMetrics() {
    Fixture fixture = new Fixture(response("{\"answer\":42}"));

    ResolvedAiModelSnapshot snapshot = fixture.resolve();
    LlmCompletionResult result = fixture.gateway.complete(request(
        List.of(LlmMessage.user("return a JSON answer")),
        new LlmResponseFormat.JsonObject(),
        List.of(),
        snapshot));

    assertThat(snapshot.providerType()).isEqualTo("deepseek");
    assertThat(snapshot.trustedMetadata()).containsEntry(AiGovernanceMetadataKeys.PROVIDER_TYPE, "deepseek");
    assertThat(result.provider().value()).isEqualTo("deepseek");
    assertThat(result.model()).isEqualTo(MODEL_ID);
    assertThat(result.structuredOutput().path("answer").asInt()).isEqualTo(42);
    assertThat(fixture.client.params).singleElement().satisfies(params -> {
      assertThat(params.model()).hasValueSatisfying(model ->
          assertThat(model.asString()).isEqualTo(MODEL_ID.value()));
      assertThat(params.reasoning()).hasValueSatisfying(reasoning ->
          assertThat(reasoning.effort().orElseThrow().asString()).isEqualTo("high"));
      assertThat(params.text()).hasValueSatisfying(text ->
          assertThat(text.format()).hasValueSatisfying(format -> assertThat(format.isJsonObject()).isTrue()));
    });
    assertThat(fixture.accounting.rows).singleElement().extracting(
        AiLlmCallUsageRow::provider,
        AiLlmCallUsageRow::model,
        AiLlmCallUsageRow::reasoningEffort)
        .containsExactly("deepseek", MODEL_ID.value(), "high");
    assertThat(fixture.accounting.updates).singleElement().extracting(
        AiLlmCallUsageUpdate::status,
        AiLlmCallUsageUpdate::provider)
        .containsExactly(AiLlmCallStatus.COMPLETED, "deepseek");
    assertMetric(fixture.registry, "high", 1d);
  }

  @Test
  void routesDeepSeekFunctionToolContinuationWithoutCrossingProviderIdentity() {
    Fixture fixture = new Fixture(toolResponse("call_1", "deepseek-local-sentinel"), response("done"));
    ResolvedAiModelSnapshot snapshot = fixture.resolve();
    List<LlmToolSpec> tools = List.of(new LlmToolSpec(
        "calculator",
        "Calculate an expression",
        JsonNodeFactory.instance.objectNode().put("type", "object"),
        true));

    LlmCompletionResult first = fixture.gateway.complete(request(
        List.of(LlmMessage.user("calculate 40 + 2")), new LlmResponseFormat.Text(), tools, snapshot));
    LlmCompletionResult second = fixture.gateway.complete(request(List.of(
        LlmMessage.user("calculate 40 + 2"),
        LlmMessage.assistantToolCalls(first.toolCalls(), first.providerContinuation()),
        LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode().put("value", 42))),
        new LlmResponseFormat.Text(),
        tools,
        snapshot));

    assertThat(first.provider().value()).isEqualTo("deepseek");
    assertThat(first.providerContinuation().providerType()).isEqualTo(DeepSeekProviderAdapter.PROVIDER_TYPE);
    assertThat(second.provider().value()).isEqualTo("deepseek");
    assertThat(second.message().text()).isEqualTo("done");
    assertThat(fixture.client.params).hasSize(2);
    assertThat(fixture.client.params.get(0).tools()).hasValueSatisfying(value ->
        assertThat(value).singleElement().satisfies(tool -> assertThat(tool.isFunction()).isTrue()));
    List<ResponseInputItem> secondInput = fixture.client.params.get(1).input().orElseThrow().asResponse().stream().toList();
    assertThat(secondInput.get(1).asReasoning().encryptedContent()).contains("deepseek-local-sentinel");
    assertThat(secondInput.get(2).asFunctionCall().callId()).isEqualTo("call_1");
    assertThat(secondInput.get(3).asFunctionCallOutput().callId()).isEqualTo("call_1");
    assertThat(fixture.accounting.rows).extracting(AiLlmCallUsageRow::provider).containsOnly("deepseek");
    assertThat(fixture.accounting.updates).extracting(AiLlmCallUsageUpdate::provider).containsOnly("deepseek");
    assertMetric(fixture.registry, "high", 2d);
  }

  @Test
  void rejectsDisabledProviderAndUnknownRouteBeforeCreatingTheFakeClient() {
    Fixture disabled = new Fixture(response("unused"));
    disabled.providerEnabled = false;

    assertThatThrownBy(disabled::resolve)
        .isInstanceOfSatisfying(AiModelRouteException.class,
            error -> assertThat(error.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_UNAVAILABLE));
    assertThat(disabled.client.calls).hasValue(0);

    Fixture unknownRoute = new Fixture(response("unused"));
    unknownRoute.route.set(policy(999L));

    assertThatThrownBy(unknownRoute::resolve)
        .isInstanceOfSatisfying(AiModelRouteException.class,
            error -> assertThat(error.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_UNAVAILABLE));
    assertThat(unknownRoute.client.calls).hasValue(0);
  }

  private static void assertMetric(SimpleMeterRegistry registry, String effort, double expectedCount) {
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "deepseek", "reasoning_effort", effort, "status", "success")
        .counter().count()).isEqualTo(expectedCount);
  }

  private static LlmCompletionRequest request(
      List<LlmMessage> messages,
      LlmResponseFormat responseFormat,
      List<LlmToolSpec> tools,
      ResolvedAiModelSnapshot snapshot
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(java.util.Set.of()))
        .messages(messages)
        .options(LlmGenerationOptions.defaults())
        .responseFormat(responseFormat)
        .tools(tools)
        .metadata(Map.of(
            AiGovernanceMetadataKeys.RUN_ID, "deepseek-local-run",
            AiGovernanceMetadataKeys.USER_ID, 7L,
            AiGovernanceMetadataKeys.PURPOSE, "LEARNING_CHAT",
            AiGovernanceMetadataKeys.SOURCE, "PRACTICE_CHAT",
            AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL"))
        .invocationTarget(snapshot.invocationTarget())
        .build();
  }

  private static ResolvedPolicy<AiModelRoutePolicyContent> policy(long modelId) {
    return new ResolvedPolicy<>(
        17L,
        "ai.model-route.practice-chat.v1",
        "DeepSeek local route",
        1,
        new AiModelRoutePolicyContent(modelId, LlmReasoningEffort.HIGH),
        PolicyMatchSource.GROUP,
        9L,
        1L);
  }

  private static Response response(String text) {
    return baseResponse().addOutput(ResponseOutputMessage.builder()
        .id("msg_1")
        .status(ResponseOutputMessage.Status.COMPLETED)
        .addContent(ResponseOutputText.builder().text(text).annotations(List.of()).build())
        .build()).build();
  }

  private static Response toolResponse(String callId, String encryptedContent) {
    return baseResponse().output(List.of(
        com.openai.models.responses.ResponseOutputItem.ofReasoning(ResponseReasoningItem.builder()
            .id("reasoning_1")
            .summary(List.of())
            .type(JsonValue.from("reasoning"))
            .encryptedContent(encryptedContent)
            .build()),
        com.openai.models.responses.ResponseOutputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
            .callId(callId)
            .name("calculator")
            .arguments("{\"expression\":\"40 + 2\"}")
            .build()))).build();
  }

  private static Response.Builder baseResponse() {
    return Response.builder()
        .id("local-response")
        .createdAt(1.0)
        .error(Optional.empty())
        .incompleteDetails(Optional.empty())
        .instructions(Optional.empty())
        .metadata(Optional.empty())
        .model(MODEL_ID.value())
        .parallelToolCalls(false)
        .toolChoice(ToolChoiceOptions.AUTO)
        .tools(List.of())
        .temperature(Optional.empty())
        .topP(Optional.empty())
        .background(Optional.empty())
        .completedAt(Optional.empty())
        .conversation(Optional.empty())
        .maxOutputTokens(Optional.empty())
        .maxToolCalls(Optional.empty())
        .moderation(Optional.empty())
        .previousResponseId(Optional.empty())
        .prompt(Optional.empty())
        .promptCacheRetention(Optional.empty())
        .reasoning(Optional.empty())
        .serviceTier(Optional.empty())
        .status(ResponseStatus.COMPLETED)
        .text(JsonField.ofNullable(null))
        .topLogprobs(Optional.empty())
        .truncation(Optional.empty())
        .usage(ResponseUsage.builder()
            .inputTokens(3)
            .inputTokensDetails(ResponseUsage.InputTokensDetails.builder().cachedTokens(1).build())
            .outputTokens(4)
            .outputTokensDetails(ResponseUsage.OutputTokensDetails.builder().reasoningTokens(2).build())
            .totalTokens(7)
            .build())
        .user("");
  }

  private static final class Fixture {

    private final AtomicReference<ResolvedPolicy<AiModelRoutePolicyContent>> route = new AtomicReference<>(policy(101L));
    private final FakeResponsesClient client;
    private final RecordingAccounting accounting = new RecordingAccounting();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AiConfiguredModelRepository models = mock(AiConfiguredModelRepository.class);
    private final AiProviderInstanceRepository providers = mock(AiProviderInstanceRepository.class);
    private final DefaultAiModelRouteResolver resolver;
    private final LlmGateway gateway;
    private boolean providerEnabled = true;

    private Fixture(Response... responses) {
      client = new FakeResponsesClient(List.of(responses));
      DeepSeekProviderAdapter adapter = new DeepSeekProviderAdapter(config -> client);
      AiProviderManagementService management = mock(AiProviderManagementService.class);
      GenericPolicyQueryService policies = new GenericPolicyQueryService() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<ResolvedPolicy<T>> resolve(GenericPolicyType<T> type, long userId) {
          return Optional.ofNullable((ResolvedPolicy<T>) route.get());
        }
      };
      when(models.findById(101L)).thenReturn(Optional.of(new AiConfiguredModel(
          101L, 11L, "DeepSeek local model", MODEL_ID.value(), true, VERSION, VERSION)));
      when(providers.findById(11L)).thenAnswer(invocation -> Optional.of(new AiProviderInstance(
          11L,
          "DeepSeek local provider",
          DeepSeekProviderProfile.PROVIDER_TYPE.value(),
          providerEnabled,
          JsonNodeFactory.instance.objectNode()
              .put("apiKey", "local-fixture-key")
              .put("baseUrl", "https://fixture.invalid")
              .put("timeoutSeconds", 30)
              .put("maxRetries", 0),
          VERSION,
          VERSION)));
      resolver = new DefaultAiModelRouteResolver(
          policies,
          new AiModelRoutePolicyTypeContributor(management),
          models,
          providers,
          new LlmProviderAdapterRegistry(List.of(adapter)),
          new ProviderClientRegistry());
      AiLlmCallAccountingService accountingService = new AiLlmCallAccountingService(
          accounting,
          new NoopUsageStore(),
          new AiLlmCallContextResolver(),
          Clock.fixed(VERSION, ZoneOffset.UTC),
          ZoneOffset.UTC,
          null);
      gateway = new AiAccountingLlmGateway(
          new AiProviderCallMetricsLlmGateway(new DynamicLlmGateway(), registry),
          accountingService);
    }

    private ResolvedAiModelSnapshot resolve() {
      return resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L);
    }
  }

  private static final class FakeResponsesClient implements OpenAiCompatibleResponsesClient {

    private final List<Response> responses;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<com.openai.models.responses.ResponseCreateParams> params = new ArrayList<>();

    private FakeResponsesClient(List<Response> responses) {
      this.responses = responses;
    }

    @Override
    public Response create(com.openai.models.responses.ResponseCreateParams value) {
      params.add(value);
      return responses.get(calls.getAndIncrement());
    }

    @Override
    public StreamResponse<ResponseStreamEvent> createStreaming(com.openai.models.responses.ResponseCreateParams value) {
      throw new UnsupportedOperationException("Streaming is covered by the DeepSeek provider client fixture");
    }
  }

  private static final class RecordingAccounting implements AiLlmCallUsageMapper {

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
}
