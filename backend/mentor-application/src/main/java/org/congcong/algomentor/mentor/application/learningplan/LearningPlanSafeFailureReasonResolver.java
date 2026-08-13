package org.congcong.algomentor.mentor.application.learningplan;

import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;

/**
 * 将已知业务异常转换为可安全展示给学习者的失败原因。
 *
 * <p>不得把任意 {@link Throwable#getMessage()} 传给前端，以免暴露内部实现或敏感上下文。</p>
 */
public final class LearningPlanSafeFailureReasonResolver {

  private LearningPlanSafeFailureReasonResolver() {
  }

  public static String resolve(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof AiRunAdmissionException admission
          && admission.code() == AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT) {
        return admission.getMessage();
      }
      current = current.getCause();
    }
    return null;
  }
}
