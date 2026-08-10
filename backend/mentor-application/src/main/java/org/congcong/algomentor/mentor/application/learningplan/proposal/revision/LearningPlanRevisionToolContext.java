package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

/** 从 Agent 受信 metadata 解析当前学习计划修订身份。 */
final class LearningPlanRevisionToolContext {

  private LearningPlanRevisionToolContext() {
  }

  static TrustedContext resolve(AgentExecutionContext context) {
    if (context == null) {
      return TrustedContext.failed("MISSING_TRUSTED_CONTEXT");
    }
    Map<String, Object> metadata = context.requestMetadata();
    String scenario = text(metadata.get(LearningPlanRevisionToolContracts.METADATA_SCENARIO));
    Long userId = positiveLong(metadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long revisionId = positiveLong(metadata.get(LearningPlanRevisionToolContracts.METADATA_REVISION_ID));
    if (!LearningPlanRevisionToolContracts.SCENARIO.equals(scenario)) {
      return TrustedContext.failed("INVALID_SCENARIO");
    }
    if (userId == null || revisionId == null) {
      return TrustedContext.failed("MISSING_TRUSTED_CONTEXT");
    }
    return new TrustedContext(userId, revisionId, null);
  }

  private static Long positiveLong(Object value) {
    if (value instanceof Number number) {
      long parsed = number.longValue();
      return parsed > 0 ? parsed : null;
    }
    if (value instanceof CharSequence text) {
      try {
        long parsed = Long.parseLong(text.toString().trim());
        return parsed > 0 ? parsed : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }

  record TrustedContext(Long userId, Long revisionId, String failureCode) {

    static TrustedContext failed(String code) {
      return new TrustedContext(null, null, code);
    }
  }
}
