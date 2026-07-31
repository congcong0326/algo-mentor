package org.congcong.algomentor.mentor.application.profile.operation.model;

import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;

/** 模型候选的四种合法变更；用户、grade、状态和时间均由服务端补全。 */
public sealed interface LearnerMemoryOperation permits LearnerMemoryOperation.Add,
    LearnerMemoryOperation.Confirm, LearnerMemoryOperation.Revise, LearnerMemoryOperation.Retire {

  LearnerMemoryClaimContract.OperationAction action();

  LearnerMemoryEvidenceReferences evidence();

  String decisionReason();

  default Long targetId() {
    return null;
  }

  record Add(
      LearnerMemoryClaimScope scope,
      String claimText,
      LearnerMemoryEvidenceReferences evidence,
      String decisionReason
  ) implements LearnerMemoryOperation {
    public Add {
      if (scope == null || claimText == null || claimText.isBlank() || evidence == null) {
        throw new IllegalArgumentException("ADD operation 字段非法。");
      }
      claimText = claimText.trim();
      decisionReason = normalizeReason(decisionReason);
    }

    @Override
    public LearnerMemoryClaimContract.OperationAction action() {
      return LearnerMemoryClaimContract.OperationAction.ADD;
    }
  }

  record Confirm(
      long targetRevisionId,
      LearnerMemoryEvidenceReferences evidence,
      String decisionReason
  ) implements LearnerMemoryOperation {
    public Confirm {
      requireTarget(targetRevisionId);
      if (evidence == null) {
        throw new IllegalArgumentException("CONFIRM operation evidence 不能为空。");
      }
      decisionReason = normalizeReason(decisionReason);
    }

    @Override
    public LearnerMemoryClaimContract.OperationAction action() {
      return LearnerMemoryClaimContract.OperationAction.CONFIRM;
    }

    @Override
    public Long targetId() {
      return targetRevisionId;
    }
  }

  record Revise(
      long targetRevisionId,
      String claimText,
      LearnerMemoryEvidenceReferences evidence,
      String decisionReason
  ) implements LearnerMemoryOperation {
    public Revise {
      requireTarget(targetRevisionId);
      if (claimText == null || claimText.isBlank() || evidence == null) {
        throw new IllegalArgumentException("REVISE operation 字段非法。");
      }
      claimText = claimText.trim();
      decisionReason = normalizeReason(decisionReason);
    }

    @Override
    public LearnerMemoryClaimContract.OperationAction action() {
      return LearnerMemoryClaimContract.OperationAction.REVISE;
    }

    @Override
    public Long targetId() {
      return targetRevisionId;
    }
  }

  record Retire(
      long targetRevisionId,
      LearnerMemoryEvidenceReferences evidence,
      String decisionReason
  ) implements LearnerMemoryOperation {
    public Retire {
      requireTarget(targetRevisionId);
      if (evidence == null) {
        throw new IllegalArgumentException("RETIRE operation evidence 不能为空。");
      }
      decisionReason = normalizeReason(decisionReason);
    }

    @Override
    public LearnerMemoryClaimContract.OperationAction action() {
      return LearnerMemoryClaimContract.OperationAction.RETIRE;
    }

    @Override
    public Long targetId() {
      return targetRevisionId;
    }
  }

  private static void requireTarget(long targetRevisionId) {
    if (targetRevisionId <= 0) {
      throw new IllegalArgumentException("target revision id 必须为正数。");
    }
  }

  private static String normalizeReason(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
