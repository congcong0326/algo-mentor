package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProvider;
import org.congcong.algomentor.llm.core.provider.LlmProviderCapabilities;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenAiLlmProvider implements LlmProvider {

  private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);
  public static final LlmProviderId PROVIDER_ID = LlmProviderId.of("openai");
  private static final Set<LlmCapability> SUPPORTED_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.TOKEN_USAGE,
      LlmCapability.CACHED_TOKEN_USAGE);

  private final OpenAiLlmProperties properties;
  private final OpenAiResponsesClient client;
  private final OpenAiResponsesMapper mapper;

  public OpenAiLlmProvider(OpenAiLlmProperties properties, OpenAiResponsesClient client) {
    this(properties, client, new ObjectMapper());
  }

  public OpenAiLlmProvider(OpenAiLlmProperties properties, OpenAiResponsesClient client, ObjectMapper objectMapper) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.properties.validate();
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.mapper = new OpenAiResponsesMapper(Objects.requireNonNull(objectMapper, "objectMapper must not be null"), PROVIDER_ID);
  }

  public static OpenAiLlmProvider create(OpenAiLlmProperties properties) {
    return new OpenAiLlmProvider(properties, OpenAiResponsesClient.fromProperties(properties));
  }

  @Override
  public LlmProviderId id() {
    return PROVIDER_ID;
  }

  @Override
  public LlmProviderCapabilities capabilities() {
    return new LlmProviderCapabilities(SUPPORTED_CAPABILITIES, Map.of(modelId().value(), descriptor()));
  }

  @Override
  public List<LlmModelDescriptor> models() {
    return List.of(descriptor());
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    Instant startedAt = Instant.now();
    ensureEnabled();
    LlmModelId modelId = resolvedModel(request);
    try {
      log.info(
          "OpenAI completion request started. provider={} model={} baseUrlHost={} timeoutMs={} maxRetries={} messageCount={} responseFormat={}",
          PROVIDER_ID.value(),
          modelId.value(),
          properties.getBaseUrl().getHost(),
          properties.getTimeout().toMillis(),
          properties.getMaxRetries(),
          request.messages().size(),
          responseFormatName(request.responseFormat()));
      LlmCompletionResult result = mapper.toResult(client.create(mapper.toParams(request, modelId)));
      log.info(
          "OpenAI completion request completed. provider={} model={} finishReason={} elapsedMs={} usage=input:{},output:{},total:{}",
          PROVIDER_ID.value(),
          modelId.value(),
          result.finishReason(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          result.usage().inputTokens(),
          result.usage().outputTokens(),
          result.usage().totalTokens());
      return result;
    } catch (Throwable error) {
      LlmException mapped = OpenAiLlmExceptionMapper.map(error, PROVIDER_ID, modelId);
      log.warn(
          "OpenAI completion request failed. provider={} model={} elapsedMs={} code={} retryable={} metadata={} causeType={} causeMessage={}",
          PROVIDER_ID.value(),
          modelId.value(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          mapped.code(),
          mapped.retryable(),
          mapped.metadata(),
          causeType(mapped),
          causeMessage(mapped));
      throw mapped;
    }
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    ensureEnabled();
    LlmModelId modelId = resolvedModel(request);
    try {
      return new OpenAiStreamPublisher(client.createStreaming(mapper.toParams(request, modelId)), mapper, PROVIDER_ID, modelId);
    } catch (Throwable error) {
      throw OpenAiLlmExceptionMapper.map(error, PROVIDER_ID, modelId);
    }
  }

  private LlmModelDescriptor descriptor() {
    return new LlmModelDescriptor(
        PROVIDER_ID,
        modelId(),
        properties.getModel(),
        SUPPORTED_CAPABILITIES,
        0,
        0,
        LlmGenerationOptions.defaults(),
        Map.of(LlmMetadataKeys.API, "responses"));
  }

  private LlmModelId resolvedModel(LlmCompletionRequest request) {
    return request.modelSelector().modelId().orElse(modelId());
  }

  private LlmModelId modelId() {
    return LlmModelId.of(properties.getModel());
  }

  private void ensureEnabled() {
    if (!properties.isEnabled()) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "OpenAI LLM provider is disabled",
          PROVIDER_ID,
          modelId(),
          false,
          Map.of(LlmMetadataKeys.PROVIDER, PROVIDER_ID.value()),
          null);
    }
  }

  private String responseFormatName(org.congcong.algomentor.llm.core.request.LlmResponseFormat responseFormat) {
    if (responseFormat instanceof org.congcong.algomentor.llm.core.request.LlmResponseFormat.JsonSchema schema) {
      return "JsonSchema:%s".formatted(schema.name());
    }
    return responseFormat.getClass().getSimpleName();
  }

  private String causeType(Throwable error) {
    Throwable cause = error.getCause();
    return cause == null ? "none" : cause.getClass().getName();
  }

  private String causeMessage(Throwable error) {
    Throwable cause = error.getCause();
    if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
      return "";
    }
    return cause.getMessage();
  }
}
