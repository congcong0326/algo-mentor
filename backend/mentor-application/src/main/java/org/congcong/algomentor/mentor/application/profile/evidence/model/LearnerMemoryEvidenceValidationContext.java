package org.congcong.algomentor.mentor.application.profile.evidence.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 由受信查询构建的本次 evidence 可见范围，不能由模型或前端直接构造输入。 */
public final class LearnerMemoryEvidenceValidationContext {

  private final Map<Long, ReviewSource> reviews;
  private final Map<Long, MessageSource> messages;

  public LearnerMemoryEvidenceValidationContext(List<ReviewSource> reviews, List<MessageSource> messages) {
    this.reviews = indexed(reviews, ReviewSource::reviewId, "review sources");
    this.messages = indexed(messages, MessageSource::messageId, "message sources");
  }

  public ReviewSource review(long reviewId) {
    return reviews.get(reviewId);
  }

  public MessageSource message(long messageId) {
    return messages.get(messageId);
  }

  public record ReviewSource(
      long reviewId,
      String problemSlug,
      int versionNo,
      boolean passed,
      Set<Long> tagIds,
      Instant createdAt
  ) {
    public ReviewSource {
      if (reviewId <= 0 || problemSlug == null || problemSlug.isBlank() || versionNo <= 0 || createdAt == null) {
        throw new IllegalArgumentException("review evidence source 非法。");
      }
      problemSlug = problemSlug.trim();
      tagIds = tagIds == null ? Set.of() : tagIds.stream()
          .filter(tagId -> tagId != null && tagId > 0)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
  }

  public record MessageSource(long messageId, Instant createdAt) {
    public MessageSource {
      if (messageId <= 0 || createdAt == null) {
        throw new IllegalArgumentException("message evidence source 非法。");
      }
    }
  }

  private static <T> Map<Long, T> indexed(
      List<T> values,
      java.util.function.Function<T, Long> id,
      String field) {
    Map<Long, T> result = new LinkedHashMap<>();
    for (T value : values == null ? List.<T>of() : values) {
      if (value == null || result.putIfAbsent(id.apply(value), value) != null) {
        throw new IllegalArgumentException(field + " 不能为空且不能重复。");
      }
    }
    return Map.copyOf(result);
  }
}
