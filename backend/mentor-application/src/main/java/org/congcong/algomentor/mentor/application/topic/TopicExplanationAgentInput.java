package org.congcong.algomentor.mentor.application.topic;

import java.util.Map;
import org.congcong.algomentor.domain.learning.LearningTopic;

/** Topic 讲解 Definition 的受信业务输入。 */
public record TopicExplanationAgentInput(
    long userId,
    LearningTopic topic,
    String idempotencyKey,
    Map<String, Object> displayMetadata
) {

  public TopicExplanationAgentInput {
    if (userId < 1) {
      throw new IllegalArgumentException("Topic explanation user id must be positive");
    }
    if (topic == null) {
      throw new IllegalArgumentException("Topic explanation topic must not be null");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Topic explanation idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
    displayMetadata = displayMetadata == null ? Map.of() : Map.copyOf(displayMetadata);
  }
}
