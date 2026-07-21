package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.queue.consumer.BatchConsumerPolicy;
import org.congcong.algomentor.queue.consumer.BatchQueueConsumer;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 固定 topic 的严格满批消费者；callback 失败不可改变此前的 SUCCEEDED 出队状态。 */
public final class CodeReviewProfileBatchConsumer implements BatchQueueConsumer {

  private static final Logger log = LoggerFactory.getLogger(CodeReviewProfileBatchConsumer.class);
  private static final BatchConsumerPolicy POLICY = new BatchConsumerPolicy(CodeReviewProfileConsumerConstants.BATCH_SIZE);

  private final ObjectMapper objectMapper;
  private final CodeReviewProfileFactRepository factRepository;
  private final CodeReviewProfileUpdateService updateService;
  private final CodeReviewProfileMetrics metrics;

  public CodeReviewProfileBatchConsumer(
      ObjectMapper objectMapper,
      CodeReviewProfileFactRepository factRepository,
      CodeReviewProfileUpdateService updateService
  ) {
    this(objectMapper, factRepository, updateService, CodeReviewProfileMetrics.NOOP);
  }

  public CodeReviewProfileBatchConsumer(
      ObjectMapper objectMapper,
      CodeReviewProfileFactRepository factRepository,
      CodeReviewProfileUpdateService updateService,
      CodeReviewProfileMetrics metrics
  ) {
    this.objectMapper = objectMapper;
    this.factRepository = factRepository;
    this.updateService = updateService;
    this.metrics = metrics == null ? CodeReviewProfileMetrics.NOOP : metrics;
  }

  @Override
  public Set<String> topics() {
    return Set.of(CodeReviewProfileQueueContracts.TOPIC);
  }

  @Override
  public BatchConsumerPolicy policy() {
    return POLICY;
  }

  @Override
  public void consume(List<QueueMessage> messages) {
    long startedAt = System.nanoTime();
    try {
      Batch batch = validate(messages);
      List<CodeReviewProfileFact> facts = factRepository.findByReviewIds(batch.userId(), batch.reviewIds());
      if (facts.size() != batch.reviewIds().size()
          || facts.stream().map(CodeReviewProfileFact::reviewId).collect(java.util.stream.Collectors.toSet()).size()
          != batch.reviewIds().size()) {
        throw new IllegalArgumentException("Code review profile batch review ownership is invalid");
      }
      CodeReviewProfileUpdateResult result = updateService.update(batch.userId(), facts);
      metrics.recordResult(result, elapsed(startedAt));
      log.info("Code review profile batch consumed. messageCount={} distinctReviewCount={} outcome={} windowProblems={} appliedCount={}",
          messages.size(), batch.reviewIds().size(), result.status(), result.windowProblemCount(), result.appliedCount());
    } catch (RuntimeException exception) {
      metrics.recordCallbackFailure(elapsed(startedAt));
      log.warn("Code review profile batch callback failed after dequeue. messageCount={} exceptionType={}",
          messages == null ? 0 : messages.size(), exception.getClass().getSimpleName());
    }
  }

  private Duration elapsed(long startedAt) {
    return Duration.ofNanos(System.nanoTime() - startedAt);
  }

  private Batch validate(List<QueueMessage> messages) {
    if (messages == null || messages.size() != CodeReviewProfileConsumerConstants.BATCH_SIZE) {
      throw new IllegalArgumentException("Code review profile consumer requires a full batch");
    }
    QueueMessage first = messages.get(0);
    if (!CodeReviewProfileQueueContracts.TOPIC.equals(first.topic())) {
      throw new IllegalArgumentException("Code review profile consumer topic is invalid");
    }
    long userId = userId(first.key());
    Set<Long> reviewIds = new HashSet<>();
    for (QueueMessage message : messages) {
      if (!CodeReviewProfileQueueContracts.TOPIC.equals(message.topic())
          || !first.key().equals(message.key()) || !reviewIds.add(reviewId(message.value()))) {
        throw new IllegalArgumentException("Code review profile consumer batch is invalid");
      }
    }
    return new Batch(userId, reviewIds.stream().sorted().toList());
  }

  private long userId(String key) {
    try {
      long userId = Long.parseLong(key);
      if (!CodeReviewProfileQueueContracts.keyForUser(userId).equals(key)) {
        throw new IllegalArgumentException("Code review profile key is invalid");
      }
      return userId;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Code review profile key is invalid", exception);
    }
  }

  private long reviewId(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      if (root == null || !root.isObject() || root.size() != 1 || !root.has(CodeReviewProfileQueueContracts.JSON_REVIEW_ID)) {
        throw new IllegalArgumentException("Code review profile payload is invalid");
      }
      JsonNode reviewId = root.path(CodeReviewProfileQueueContracts.JSON_REVIEW_ID);
      if (!reviewId.isIntegralNumber() || !reviewId.canConvertToLong() || reviewId.asLong() < 1) {
        throw new IllegalArgumentException("Code review profile payload review id is invalid");
      }
      return reviewId.asLong();
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException("Code review profile payload is invalid", exception);
    }
  }

  private record Batch(long userId, List<Long> reviewIds) {
  }
}
