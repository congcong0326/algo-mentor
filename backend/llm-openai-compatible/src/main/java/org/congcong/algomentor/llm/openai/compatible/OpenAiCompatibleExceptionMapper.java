package org.congcong.algomentor.llm.openai.compatible;

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

public final class OpenAiCompatibleExceptionMapper {

  private static final String SERVER_IS_OVERLOADED = "server_is_overloaded";

  private OpenAiCompatibleExceptionMapper() {
  }

  public static LlmException map(
      Throwable error,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId
  ) {
    var providerId = profile.providerId();
    if (error instanceof LlmException llmException) {
      return llmException;
    }
    if (error instanceof SseException sseException) {
      return mapSseException(sseException, profile, modelId);
    }
    if (error instanceof OpenAIServiceException serviceException) {
      return mapServiceException(serviceException, profile, modelId);
    }
    if (error instanceof OpenAIRetryableException || error instanceof OpenAIIoException) {
      return new LlmException(
          LlmErrorCode.PROVIDER_UNAVAILABLE,
          safeMessage(error, profile),
          providerId,
          modelId,
          true,
          Map.of(LlmMetadataKeys.PROVIDER, providerId.value()),
          error);
    }
    return new LlmException(
        LlmErrorCode.UNKNOWN,
        safeMessage(error, profile),
        providerId,
        modelId,
        false,
        Map.of(LlmMetadataKeys.PROVIDER, providerId.value()),
        error);
  }

  public static LlmException streamError(
      String message,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId,
      Map<String, Object> metadata
  ) {
    return new LlmException(
        LlmErrorCode.PROVIDER_UNAVAILABLE,
        profile.safeStreamErrorMessage(message),
        profile.providerId(),
        modelId,
        true,
        metadata == null ? Map.of() : metadata,
        null);
  }

  static boolean isServerOverloaded(LlmException error) {
    return SERVER_IS_OVERLOADED.equals(error.metadata().get(LlmMetadataKeys.ERROR_CODE));
  }

  private static LlmException mapServiceException(
      OpenAIServiceException error,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId
  ) {
    var providerId = profile.providerId();
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
    return new LlmException(code, safeMessage(error, profile), providerId, modelId, retryable, metadata, error);
  }

  private static LlmException mapSseException(
      SseException error,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId
  ) {
    var providerId = profile.providerId();
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LlmMetadataKeys.PROVIDER, providerId.value());
    metadata.put(LlmMetadataKeys.STATUS_CODE, error.statusCode());
    error.code().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_CODE, value));
    error.type().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_TYPE, value));
    error.param().ifPresent(value -> metadata.put(LlmMetadataKeys.ERROR_PARAM, value));
    return new LlmException(
        LlmErrorCode.PROVIDER_UNAVAILABLE,
        safeMessage(error, profile),
        providerId,
        modelId,
        true,
        metadata,
        error);
  }

  private static String safeMessage(Throwable error, OpenAiCompatibleProviderProfile profile) {
    String message = error.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }
    return profile.safeProviderErrorMessage();
  }
}
