package org.congcong.algomentor.queue.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.congcong.algomentor.queue.PersistentQueueConstants;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用共享 Jackson 与当前事务发布 PostgreSQL PENDING 消息。 */
public class PostgresQueuePublisher implements QueuePublisher {

  private final ObjectMapper objectMapper;
  private final QueueMessageRepository repository;
  private final PersistentQueueProperties properties;

  public PostgresQueuePublisher(
      ObjectMapper objectMapper,
      QueueMessageRepository repository,
      PersistentQueueProperties properties) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public QueueMessage publish(String topic, String key, Object payload) {
    String normalizedTopic = requireText(topic, "topic", PersistentQueueConstants.MAX_TOPIC_LENGTH);
    String normalizedKey = requireText(key, "key", PersistentQueueConstants.MAX_KEY_LENGTH);
    if (payload == null) {
      throw new QueuePublishException("Persistent queue payload must not be null");
    }
    String value = serialize(payload);
    int size = value.getBytes(StandardCharsets.UTF_8).length;
    if (size > properties.getMessage().getMaxValueBytes()) {
      throw new QueuePublishException("Persistent queue message value exceeds configured byte limit");
    }
    return repository.insertPending(normalizedTopic, normalizedKey, value);
  }

  private String serialize(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new QueuePublishException("Persistent queue payload cannot be serialized", exception);
    }
  }

  private String requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new QueuePublishException("Persistent queue " + field + " must not be blank");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new QueuePublishException("Persistent queue " + field + " exceeds maximum length");
    }
    return normalized;
  }
}
