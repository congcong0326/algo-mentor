package org.congcong.algomentor.ai.governance.accounting;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;

/** 将 LLM request metadata 解析成调用级台账上下文，并在缺失时保留 UNKNOWN 防线。 */
public final class AiLlmCallContextResolver {

  public AiLlmCallContext resolve(LlmCompletionRequest request) {
    Map<String, Object> metadata = request == null ? Map.of() : request.metadata();
    Integer stepIndex = positiveInteger(metadata.get(AgentRuntimeMetadataKeys.STEP_INDEX));
    Long userId = positiveLong(metadata.get(AiGovernanceMetadataKeys.USER_ID));
    String runId = text(metadata.get(AiGovernanceMetadataKeys.RUN_ID));
    String purpose = text(metadata.get(AiGovernanceMetadataKeys.PURPOSE));
    String source = text(metadata.get(AiGovernanceMetadataKeys.SOURCE));
    String quotaScope = text(metadata.get(AiGovernanceMetadataKeys.QUOTA_SCOPE));
    AiLlmCallKind callKind = callKind(metadata.get(AiGovernanceMetadataKeys.CALL_KIND), stepIndex);
    boolean missingContext = userId == null || purpose == null || source == null;
    return new AiLlmCallContext(
        UUID.randomUUID().toString(),
        runId,
        userId,
        purpose,
        source,
        callKind,
        stepIndex,
        quotaScope,
        missingContext);
  }

  private static AiLlmCallKind callKind(Object value, Integer stepIndex) {
    String text = text(value);
    if (text == null) {
      return stepIndex == null ? AiLlmCallKind.DIRECT : AiLlmCallKind.AGENT_STEP;
    }
    try {
      return AiLlmCallKind.valueOf(text.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return stepIndex == null ? AiLlmCallKind.DIRECT : AiLlmCallKind.AGENT_STEP;
    }
  }

  private static String text(Object value) {
    if (!(value instanceof String text) || text.isBlank()) {
      return null;
    }
    return text.trim();
  }

  private static Long positiveLong(Object value) {
    if (value instanceof Number number && number.longValue() > 0) {
      return number.longValue();
    }
    if (value instanceof String text) {
      try {
        long parsed = Long.parseLong(text.trim());
        return parsed > 0 ? parsed : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Integer positiveInteger(Object value) {
    if (value instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        int parsed = Integer.parseInt(text.trim());
        return parsed > 0 ? parsed : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
