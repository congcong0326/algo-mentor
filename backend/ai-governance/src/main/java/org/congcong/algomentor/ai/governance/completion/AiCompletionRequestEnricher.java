package org.congcong.algomentor.ai.governance.completion;

import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;

/** 将受信治理上下文写入最终 provider request 的 metadata。 */
final class AiCompletionRequestEnricher {

  private AiCompletionRequestEnricher() {
  }

  static LlmCompletionRequest enrich(
      LlmCompletionRequest request,
      AiCompletionContext context,
      Map<String, Object> admissionMetadata
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (request.metadata() != null) {
      metadata.putAll(request.metadata());
    }
    if (context.metadata() != null) {
      metadata.putAll(context.metadata());
    }
    if (admissionMetadata != null) {
      metadata.putAll(admissionMetadata);
    }
    metadata.put(AiGovernanceMetadataKeys.USER_ID, context.userId());
    if (context.runId() != null && !context.runId().isBlank()) {
      metadata.put(AiGovernanceMetadataKeys.RUN_ID, context.runId());
    }
    metadata.put(AiGovernanceMetadataKeys.PURPOSE, context.purpose().name());
    metadata.put(AiGovernanceMetadataKeys.SOURCE, context.source().name());
    metadata.put(AiGovernanceMetadataKeys.CALL_KIND, context.callKind().name());
    metadata.put(AiGovernanceMetadataKeys.QUOTA_SCOPE, context.quotaScope());
    if (context.stepIndex() != null) {
      metadata.put(AgentRuntimeMetadataKeys.STEP_INDEX, context.stepIndex());
    }
    return new LlmCompletionRequest(
        request.modelSelector(),
        request.messages(),
        request.options(),
        request.tools(),
        request.toolChoice(),
        request.responseFormat(),
        metadata);
  }
}
