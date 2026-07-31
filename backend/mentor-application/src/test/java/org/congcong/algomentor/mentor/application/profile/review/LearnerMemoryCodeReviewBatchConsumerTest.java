package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewBatchConsumerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void consumesOnlyOneStrictFiveMessageUserBatchAndNeverThrowsCallbackFailures() {
    RecordingFactRepository facts = new RecordingFactRepository();
    RecordingUpdateService updates = new RecordingUpdateService();
    RecordingMetrics metrics = new RecordingMetrics();
    LearnerMemoryCodeReviewBatchConsumer consumer = new LearnerMemoryCodeReviewBatchConsumer(objectMapper, facts, updates, metrics);

    consumer.consume(messages(7L, List.of(1L, 2L, 3L, 4L, 5L)));
    consumer.consume(messages(7L, List.of(1L, 2L, 3L, 4L, 4L)));

    assertThat(consumer.topics()).containsExactly(LearnerMemoryCodeReviewQueueContracts.TOPIC);
    assertThat(consumer.policy().batchSize()).isEqualTo(LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE);
    assertThat(facts.calls).hasSize(1);
    assertThat(updates.calls).hasSize(1);
    assertThat(updates.calls.get(0)).extracting(LearnerMemoryCodeReviewFact::reviewId)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(metrics.statuses).containsExactly(
        LearnerMemoryRunContract.Status.NO_CHANGE,
        LearnerMemoryRunContract.Status.FAILED);
  }

  @Test
  void rejectsCrossUserUnknownPayloadAndMissingReviewFactsBeforeAgentInvocation() {
    RecordingFactRepository facts = new RecordingFactRepository();
    RecordingUpdateService updates = new RecordingUpdateService();
    RecordingMetrics metrics = new RecordingMetrics();
    LearnerMemoryCodeReviewBatchConsumer consumer = new LearnerMemoryCodeReviewBatchConsumer(objectMapper, facts, updates, metrics);

    List<QueueMessage> crossUser = new ArrayList<>(messages(7L, List.of(1L, 2L, 3L, 4L, 5L)));
    crossUser.set(4, message(5L, 8L, "{\"reviewId\":5}"));
    consumer.consume(crossUser);

    List<QueueMessage> unknownPayload = new ArrayList<>(messages(7L, List.of(1L, 2L, 3L, 4L, 5L)));
    unknownPayload.set(4, message(5L, 7L, "{\"reviewId\":5,\"unexpected\":true}"));
    consumer.consume(unknownPayload);

    facts.omitLastFact = true;
    consumer.consume(messages(7L, List.of(1L, 2L, 3L, 4L, 5L)));

    assertThat(facts.calls).hasSize(1);
    assertThat(updates.calls).isEmpty();
    assertThat(metrics.statuses).containsExactly(
        LearnerMemoryRunContract.Status.FAILED,
        LearnerMemoryRunContract.Status.FAILED,
        LearnerMemoryRunContract.Status.FAILED);
  }

  private List<QueueMessage> messages(long userId, List<Long> reviewIds) {
    List<QueueMessage> messages = new ArrayList<>();
    for (int index = 0; index < reviewIds.size(); index++) {
      messages.add(message(
          index + 1L,
          userId,
          "{\"reviewId\":" + reviewIds.get(index) + "}"));
    }
    return messages;
  }

  private QueueMessage message(long messageId, long userId, String payload) {
    return new QueueMessage(
        messageId,
        LearnerMemoryCodeReviewQueueContracts.TOPIC,
        LearnerMemoryCodeReviewQueueContracts.keyForUser(userId),
        payload,
        Instant.EPOCH);
  }

  private static LearnerMemoryCodeReviewFact fact(long reviewId) {
    BigDecimal one = BigDecimal.ONE;
    return new LearnerMemoryCodeReviewFact(
        reviewId, "problem-" + reviewId, 1, one, one, one, one, one, one, false,
        List.of(), List.of(), List.of(), Instant.EPOCH);
  }

  private static final class RecordingFactRepository implements LearnerMemoryCodeReviewFactRepository {
    private final List<List<Long>> calls = new ArrayList<>();
    private boolean omitLastFact;

    @Override
    public List<LearnerMemoryCodeReviewFact> findByReviewIds(long userId, List<Long> reviewIds) {
      calls.add(List.copyOf(reviewIds));
      return reviewIds.stream()
          .limit(omitLastFact ? reviewIds.size() - 1L : reviewIds.size())
          .map(LearnerMemoryCodeReviewBatchConsumerTest::fact)
          .toList();
    }

    @Override
    public List<LearnerMemoryCodeReviewFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryCodeReviewFact> findRecentDistinctProblems(long userId, List<String> excluded, int limit) {
      return List.of();
    }
  }

  private static final class RecordingUpdateService extends LearnerMemoryCodeReviewUpdateService {
    private final List<List<LearnerMemoryCodeReviewFact>> calls = new ArrayList<>();

    private RecordingUpdateService() {
      super(null, null, null, null, null, null, null, null, null, 1);
    }

    @Override
    public LearnerMemoryCodeReviewUpdateResult update(long userId, List<LearnerMemoryCodeReviewFact> batchFacts) {
      calls.add(List.copyOf(batchFacts));
      return new LearnerMemoryCodeReviewUpdateResult(LearnerMemoryCodeReviewUpdateResult.Status.NO_CHANGE, 1, 0);
    }
  }

  private static final class RecordingMetrics implements LearnerMemoryMetrics {
    private final List<LearnerMemoryRunContract.Status> statuses = new ArrayList<>();

    @Override
    public void recordUpdateRun(
        LearnerMemoryRunContract.Trigger trigger,
        LearnerMemoryRunContract.Status status) {
      statuses.add(status);
    }
  }
}
