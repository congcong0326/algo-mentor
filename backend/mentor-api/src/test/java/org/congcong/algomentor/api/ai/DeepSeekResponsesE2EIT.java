package org.congcong.algomentor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.llm.deepseek.DeepSeekProviderAdapter;
import org.congcong.algomentor.llm.deepseek.DeepSeekProviderProfile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * 受控环境中的真实 DeepSeek Responses 验收入口。
 *
 * <p>缺少 {@code DEEPSEEK_API_KEY} 时所有可执行场景都跳过，且不会创建网络 client。环境变量只属于
 * 本测试，不参与生产 provider 配置。</p>
 */
class DeepSeekResponsesE2EIT {

  private static final String API_KEY_ENV = "DEEPSEEK_API_KEY";
  private static final String BASE_URL_ENV = "DEEPSEEK_BASE_URL";
  private static final String MODEL_ENV = "DEEPSEEK_MODEL";
  private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
  private static final String DEFAULT_MODEL = "deepseek-reasoner";
  private static final LlmModelId MODEL = LlmModelId.of(readEnvironment(MODEL_ENV, DEFAULT_MODEL));
  private static final List<LlmToolSpec> TOOLS = List.of(
      tool("first_lookup"),
      tool("second_lookup"),
      tool("lookup"));

  @Test
  void completesShortTextWithReasoningDisabled() {
    LlmCompletionResult result = realProvider().complete(MODEL, request(
        List.of(LlmMessage.user("Reply with the word ready.")),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.Text(),
        List.of(),
        LlmToolChoice.none()));

    assertThat(result.provider().value()).isEqualTo("deepseek");
    assertThat(result.model()).isEqualTo(MODEL);
    assertThat(result.message().text()).isNotBlank();
    assertThat(result.usage().totalTokens()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void completesReasoningRequestWithHighEffort() {
    LlmCompletionResult result = realProvider().complete(MODEL, request(
        List.of(LlmMessage.user("What is 2 + 2? Reply with only the number.")),
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        List.of(),
        LlmToolChoice.none()));

    assertThat(result.provider().value()).isEqualTo("deepseek");
    assertThat(result.message().text()).isNotBlank();
    assertThat(result.usage().totalTokens()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void streamsTextWithLowEffort() throws Exception {
    CollectingSubscriber subscriber = new CollectingSubscriber();

    realProvider().stream(MODEL, request(
        List.of(LlmMessage.user("Give a one-sentence greeting.")),
        LlmReasoningEffort.LOW,
        new LlmResponseFormat.Text(),
        List.of(),
        LlmToolChoice.none())).subscribe(subscriber);

    assertThat(subscriber.finished.await(90, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.events).anyMatch(LlmStreamEvent.MessageStart.class::isInstance);
    assertThat(subscriber.events).anyMatch(LlmStreamEvent.ContentDelta.class::isInstance);
    assertThat(subscriber.events).anyMatch(LlmStreamEvent.MessageEnd.class::isInstance);
  }

  @Test
  void completesJsonSchemaWithHighEffort() {
    LlmCompletionResult result = realProvider().complete(MODEL, request(
        List.of(LlmMessage.user("Return the number four as JSON.")),
        LlmReasoningEffort.HIGH,
        numberAnswerSchema(),
        List.of(),
        LlmToolChoice.none()));

    assertThat(result.provider().value()).isEqualTo("deepseek");
    assertThat(result.structuredOutput()).isNotNull();
    assertThat(result.structuredOutput().path("answer").isInt()).isTrue();
  }

  @Test
  void mapsAnInvalidKeyToAuthenticationFailed() {
    assumeConfigured();

    assertThatThrownBy(() -> provider("deepseek-invalid-e2e-key").complete(MODEL, request(
        List.of(LlmMessage.user("authentication fixture")),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.Text(),
        List.of(),
        LlmToolChoice.none())))
        .isInstanceOfSatisfying(LlmException.class, error -> {
          assertThat(error.code()).isEqualTo(LlmErrorCode.AUTHENTICATION_FAILED);
          assertThat(error.provider().value()).isEqualTo("deepseek");
          assertThat(error.getMessage().toLowerCase(java.util.Locale.ROOT)).doesNotContain("openai");
        });
  }

  @Test
  void completesOneHighEffortToolInteraction() {
    LlmProviderClient provider = realProvider();
    LlmCompletionResult toolCall = provider.complete(MODEL, request(
        List.of(LlmMessage.user("Use first_lookup exactly once to look up the value of two plus two.")),
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.specific("first_lookup")));

    assertThat(toolCall.finishReason()).isEqualTo(LlmFinishReason.TOOL_CALLS);
    assertThat(toolCall.toolCalls()).singleElement().extracting(call -> call.name()).isEqualTo("first_lookup");
    assertThat(toolCall.providerContinuation()).isNotNull();

    LlmCompletionResult finalAnswer = provider.complete(MODEL, request(List.of(
        LlmMessage.user("Use first_lookup exactly once to look up the value of two plus two."),
        LlmMessage.assistantToolCalls(toolCall.toolCalls(), toolCall.providerContinuation()),
        LlmMessage.toolResult(toolCall.toolCalls().get(0).id(), JsonNodeFactory.instance.objectNode().put("value", 4))),
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.none()));

    assertThat(finalAnswer.provider().value()).isEqualTo("deepseek");
    assertThat(finalAnswer.message().text()).isNotBlank();
  }

  @Test
  void completesTwoSequentialHighEffortToolInteractions() {
    LlmProviderClient provider = realProvider();
    List<LlmMessage> messages = new java.util.ArrayList<>();
    messages.add(LlmMessage.user("First call first_lookup, then call second_lookup, then answer."));

    LlmCompletionResult first = provider.complete(MODEL, request(
        messages,
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.specific("first_lookup")));
    assertThat(first.providerContinuation()).isNotNull();
    messages.add(LlmMessage.assistantToolCalls(first.toolCalls(), first.providerContinuation()));
    messages.add(LlmMessage.toolResult(first.toolCalls().get(0).id(), JsonNodeFactory.instance.objectNode().put("value", "first")));

    LlmCompletionResult second = provider.complete(MODEL, request(
        messages,
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.specific("second_lookup")));
    assertThat(second.finishReason()).isEqualTo(LlmFinishReason.TOOL_CALLS);
    assertThat(second.toolCalls()).singleElement().extracting(call -> call.name()).isEqualTo("second_lookup");
    assertThat(second.providerContinuation()).isNotNull();
    messages.add(LlmMessage.assistantToolCalls(second.toolCalls(), second.providerContinuation()));
    messages.add(LlmMessage.toolResult(second.toolCalls().get(0).id(), JsonNodeFactory.instance.objectNode().put("value", "second")));

    LlmCompletionResult finalAnswer = provider.complete(MODEL, request(
        messages,
        LlmReasoningEffort.HIGH,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.none()));
    assertThat(finalAnswer.message().text()).isNotBlank();
  }

  @Test
  void completesToolInteractionWithReasoningDisabledWithoutContinuation() {
    LlmProviderClient provider = realProvider();
    LlmCompletionResult toolCall = provider.complete(MODEL, request(
        List.of(LlmMessage.user("Use lookup exactly once to look up the value of two plus two.")),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.specific("lookup")));

    assertThat(toolCall.finishReason()).isEqualTo(LlmFinishReason.TOOL_CALLS);
    assertThat(toolCall.providerContinuation()).isNull();
    LlmCompletionResult finalAnswer = provider.complete(MODEL, request(List.of(
        LlmMessage.user("Use lookup exactly once to look up the value of two plus two."),
        LlmMessage.assistantToolCalls(toolCall.toolCalls()),
        LlmMessage.toolResult(toolCall.toolCalls().get(0).id(), JsonNodeFactory.instance.objectNode().put("value", 4))),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.Text(),
        TOOLS,
        LlmToolChoice.none()));
    assertThat(finalAnswer.message().text()).isNotBlank();
  }

  @Test
  void cancellationStopsTheStreamingSubscriptionBeforeMessageEnd() throws Exception {
    CancellingSubscriber subscriber = new CancellingSubscriber();

    realProvider().stream(MODEL, request(
        List.of(LlmMessage.user("Write a detailed explanation of binary search.")),
        LlmReasoningEffort.LOW,
        new LlmResponseFormat.Text(),
        List.of(),
        LlmToolChoice.none())).subscribe(subscriber);

    assertThat(subscriber.cancelled.await(90, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.events).noneMatch(LlmStreamEvent.MessageEnd.class::isInstance);
  }

  @Disabled("Requires a controlled DeepSeek rate-limit environment; mock coverage is in RDP-12 only.")
  @Test
  void mapsControlledRateLimitToRetryableError() {
    assumeConfigured();
    throw new AssertionError("Configure a controlled rate-limit endpoint before enabling this scenario.");
  }

  private static LlmProviderClient realProvider() {
    assumeConfigured();
    return provider(requiredApiKey());
  }

  private static LlmProviderClient provider(String apiKey) {
    return new DeepSeekProviderAdapter().createClient(new LlmProviderInstanceSpec(
        1L,
        DeepSeekProviderAdapter.PROVIDER_TYPE,
        JsonNodeFactory.instance.objectNode()
            .put("apiKey", apiKey)
            .put("baseUrl", readEnvironment(BASE_URL_ENV, DEFAULT_BASE_URL))
            .put("timeoutSeconds", 90)
            .put("maxRetries", 0),
        Instant.now()));
  }

  private static LlmCompletionRequest request(
      List<LlmMessage> messages,
      LlmReasoningEffort effort,
      LlmResponseFormat responseFormat,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(DeepSeekProviderProfile.INSTANCE.providerId(), MODEL))
        .messages(messages)
        .options(LlmGenerationOptions.defaults().withReasoningEffort(effort))
        .responseFormat(responseFormat)
        .tools(tools)
        .toolChoice(toolChoice)
        .build();
  }

  private static LlmToolSpec tool(String name) {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("query").put("type", "string");
    schema.putArray("required").add("query");
    return new LlmToolSpec(
        name,
        "Return a fixed local lookup value.",
        schema,
        true);
  }

  private static LlmResponseFormat.JsonSchema numberAnswerSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.putObject("properties").putObject("answer").put("type", "integer");
    schema.putArray("required").add("answer");
    return new LlmResponseFormat.JsonSchema("number_answer", schema, true);
  }

  private static void assumeConfigured() {
    Assumptions.assumeTrue(!requiredApiKey().isBlank(), API_KEY_ENV + " is not configured");
  }

  private static String requiredApiKey() {
    return readEnvironment(API_KEY_ENV, "");
  }

  private static String readEnvironment(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<LlmStreamEvent> {

    private final List<LlmStreamEvent> events = new java.util.ArrayList<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private Throwable error;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent event) {
      events.add(event);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
      finished.countDown();
    }

    @Override
    public void onComplete() {
      finished.countDown();
    }
  }

  private static final class CancellingSubscriber implements Flow.Subscriber<LlmStreamEvent> {

    private final List<LlmStreamEvent> events = new java.util.ArrayList<>();
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription value) {
      subscription = value;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent event) {
      events.add(event);
      if (cancelled.getCount() > 0) {
        subscription.cancel();
        cancelled.countDown();
      }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }
  }
}
