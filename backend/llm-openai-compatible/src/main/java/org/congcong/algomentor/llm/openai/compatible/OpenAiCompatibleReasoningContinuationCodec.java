package org.congcong.algomentor.llm.openai.compatible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseReasoningItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;

/** 将 Responses reasoning item 限定为当前 provider 可恢复的 opaque continuation。 */
final class OpenAiCompatibleReasoningContinuationCodec {

  private static final String REASONING_TYPE = "reasoning";
  private static final String TYPE_FIELD = "type";
  private static final String ID_FIELD = "id";
  private static final String SUMMARY_FIELD = "summary";
  private static final String CONTENT_FIELD = "content";
  private static final String ENCRYPTED_CONTENT_FIELD = "encrypted_content";
  private static final String STATUS_FIELD = "status";

  private final OpenAiCompatibleProviderProfile profile;
  private final LlmModelId modelId;

  OpenAiCompatibleReasoningContinuationCodec(
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId
  ) {
    this.profile = profile;
    this.modelId = modelId;
  }

  LlmProviderContinuation create(List<ResponseReasoningItem> reasoningItems) {
    if (reasoningItems == null || reasoningItems.isEmpty()) {
      return null;
    }
    ArrayNode payload = JsonNodeFactory.instance.arrayNode();
    for (ResponseReasoningItem item : reasoningItems) {
      if (item == null) {
        throw invalidContinuation();
      }
      payload.add(toPayloadItem(item));
    }
    return new LlmProviderContinuation(profile.providerType(), payload);
  }

  List<ResponseInputItem> toInputItems(LlmProviderContinuation continuation) {
    if (continuation == null) {
      return List.of();
    }
    if (!profile.providerType().equals(continuation.providerType())) {
      throw invalidContinuation();
    }

    JsonNode payload = continuation.payload();
    if (!payload.isArray() || payload.isEmpty()) {
      throw invalidContinuation();
    }

    List<ResponseInputItem> inputItems = new ArrayList<>();
    for (JsonNode item : payload) {
      inputItems.add(ResponseInputItem.ofReasoning(toReasoningItem(item)));
    }
    return List.copyOf(inputItems);
  }

  private ObjectNode toPayloadItem(ResponseReasoningItem item) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    item._additionalProperties().forEach((name, value) -> payload.set(name, toJsonNode(value)));
    payload.put(ID_FIELD, item.id());
    payload.set(SUMMARY_FIELD, toJsonNode(item.summary()));
    payload.set(TYPE_FIELD, toJsonNode(item._type()));
    item.content().ifPresent(content -> payload.set(CONTENT_FIELD, toJsonNode(content)));
    item.encryptedContent().ifPresent(value -> payload.put(ENCRYPTED_CONTENT_FIELD, value));
    item.status().ifPresent(value -> payload.put(STATUS_FIELD, value.asString()));
    return payload;
  }

  private ResponseReasoningItem toReasoningItem(JsonNode item) {
    if (!item.isObject() || !REASONING_TYPE.equals(item.path(TYPE_FIELD).asText())) {
      throw invalidContinuation();
    }
    try {
      return JsonValue.fromJsonNode(item).convert(ResponseReasoningItem.class).validate();
    } catch (RuntimeException error) {
      throw invalidContinuation();
    }
  }

  private JsonNode toJsonNode(Object value) {
    return JsonValue.from(value).convert(JsonNode.class);
  }

  private LlmException invalidContinuation() {
    return new LlmException(
        LlmErrorCode.INVALID_REQUEST,
        profile.displayName() + " reasoning continuation is invalid",
        profile.providerId(),
        modelId,
        false,
        Map.of(),
        null);
  }
}
