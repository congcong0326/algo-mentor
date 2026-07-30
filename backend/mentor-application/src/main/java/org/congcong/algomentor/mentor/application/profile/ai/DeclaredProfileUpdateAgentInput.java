package org.congcong.algomentor.mentor.application.profile.ai;

import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;

/** Declared Profile child Definition 的受信候选输入，不携带身份或版本以外的可覆盖治理字段。 */
public record DeclaredProfileUpdateAgentInput(
    long userId,
    List<Candidate> candidates,
    String idempotencyKey,
    Long retryOfRunId
) {

  public DeclaredProfileUpdateAgentInput {
    if (userId < 1) {
      throw new IllegalArgumentException("Declared profile child user id must be positive");
    }
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("Declared profile child candidates must not be empty");
    }
    candidates = List.copyOf(candidates);
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Declared profile child idempotency key must not be blank");
    }
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Declared profile child retry source run id must be positive");
    }
    idempotencyKey = idempotencyKey.trim();
  }

  /** 已由 service 读取的单维快照和当前显式自述。 */
  public record Candidate(
      LearnerProfileDimension dimension,
      String statement,
      DeclaredProfileUpdateIntent intent,
      String currentContent
  ) {

    public Candidate {
      if (dimension == null || statement == null || statement.isBlank() || intent == null) {
        throw new IllegalArgumentException("Invalid declared profile child candidate");
      }
      statement = statement.trim();
      currentContent = currentContent == null ? "" : currentContent.trim();
    }
  }
}
