package org.congcong.algomentor.llm.openai;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.SseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;

final class OpenAiLlmExceptionMapper {

  private OpenAiLlmExceptionMapper() {
  }

  static LlmException map(Throwable error, LlmProviderId providerId, LlmModelId modelId) {
    if (error instanceof LlmException llmException) {
      return llmException;
    }
    if (error instanceof SseException sseException) {
      return mapSseException(sseException, providerId, modelId);
    }
    if (error instanceof OpenAIServiceException serviceException) {
      return mapServiceException(serviceException, providerId, modelId);
    }
    if (error instanceof OpenAIRetryableException || error instanceof OpenAIIoException) {
      return new LlmException(
          LlmErrorCode.PROVIDER_UNAVAILABLE,
          safeMessage(error),
          providerId,
          modelId,
          true,
          Map.of(LlmMetadataKeys.PROVIDER, providerId.value()),
          error);
    }
    return new LlmException(
        LlmErrorCode.UNKNOWN,
        safeMessage(error),
        providerId,
        modelId,
        false,
        Map.of(LlmMetadataKeys.PROVIDER, providerId.value()),
        error);
  }

  static LlmException streamError(String message, LlmProviderId providerId, LlmModelId modelId, Map<String, Object> metadata) {
    return new LlmException(
        LlmErrorCode.PROVIDER_UNAVAILABLE,
        message == null || message.isBlank() ? "OpenAI stream returned an error" : message,
        providerId,
        modelId,
        true,
        metadata == null ? Map.of() : metadata,
        null);
  }

  private static LlmException mapServiceException(
      OpenAIServiceException error,
      LlmProviderId providerId,
      LlmModelId modelId
  ) {
    int statusCode = error.statusCode();
    LlmErrorCode code = switch (statusCode) {
      case 401 -> LlmErrorCode.AUTHENTICATION_FAILED;
      case 403 -> LlmErrorCode.PERMISSION_DENIED;
      case 408 -> LlmErrorCode.TIMEOUT;
      case 429 -> LlmErrorCode.RATE_LIMITED;
      default -> statusCode >= 500 ? LlmErrorCode.PROVIDER_UNAVAILABLE : LlmErrorCode.INVALID_REQUEST;
    };
    boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500;
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LlmMetadataKeys.PROVIDER, providerId.value());
    metadata.put(LlmMetadataKeys.STATUS_CODE, statusCode);
    error.code().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_CODE, value));
    error.type().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_TYPE, value));
    error.param().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_PARAM, value));
    return new LlmException(code, safeMessage(error), providerId, modelId, retryable, metadata, error);
  }

  private static LlmException mapSseException(
      SseException error,
      LlmProviderId providerId,
      LlmModelId modelId
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LlmMetadataKeys.PROVIDER, providerId.value());
    metadata.put(LlmMetadataKeys.STATUS_CODE, error.statusCode());
    error.code().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_CODE, value));
    error.type().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_TYPE, value));
    error.param().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_PARAM, value));
    return new LlmException(
        LlmErrorCode.PROVIDER_UNAVAILABLE,
        safeMessage(error),
        providerId,
        modelId,
        true,
        metadata,
        error);
  }

  private static String safeMessage(Throwable error) {
    if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
      return "OpenAI provider call failed";
    }
    return error.getMessage();
  }
}
