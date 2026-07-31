package org.congcong.algomentor.mentor.application.profile.evidence.model;

import java.util.List;
import java.util.Objects;

/** 候选 operation 引用的 evidence，不接受模型提供的顺序、时间或 grade。 */
public record LearnerMemoryEvidenceReferences(
    LearnerMemoryEvidenceContract.Pattern pattern,
    List<ReviewReference> reviews,
    List<MessageReference> messages
) {

  public LearnerMemoryEvidenceReferences {
    if (pattern == null) {
      throw new IllegalArgumentException("evidence pattern 不能为空。");
    }
    reviews = immutableUnique(reviews, ReviewReference::reviewId, "review evidence");
    messages = immutableUnique(messages, MessageReference::messageId, "message evidence");
  }

  public record ReviewReference(long reviewId, LearnerMemoryEvidenceContract.ReviewRole role) {
    public ReviewReference {
      if (reviewId <= 0 || role == null) {
        throw new IllegalArgumentException("review evidence reference 非法。");
      }
    }
  }

  public record MessageReference(long messageId, LearnerMemoryEvidenceContract.MessageRole role) {
    public MessageReference {
      if (messageId <= 0 || role == null) {
        throw new IllegalArgumentException("message evidence reference 非法。");
      }
    }
  }

  private static <T> List<T> immutableUnique(
      List<T> values,
      java.util.function.Function<T, Long> id,
      String field) {
    List<T> normalized = values == null ? List.of() : List.copyOf(values);
    if (normalized.stream().anyMatch(Objects::isNull)
        || normalized.stream().map(id).distinct().count() != normalized.size()) {
      throw new IllegalArgumentException(field + " 不能为空且不能重复。");
    }
    return normalized;
  }
}
