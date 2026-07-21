package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.junit.jupiter.api.Test;

class CodeReviewProfileBatchConsumerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void consumesOnlyOneStrictFiveMessageUserBatchAndNeverThrowsCallbackFailures() {
    RecordingFactRepository facts = new RecordingFactRepository();
    RecordingUpdateService updates = new RecordingUpdateService();
    RecordingMetrics metrics = new RecordingMetrics();
    CodeReviewProfileBatchConsumer consumer = new CodeReviewProfileBatchConsumer(objectMapper, facts, updates, metrics);

    consumer.consume(messages(7L, List.of(1L, 2L, 3L, 4L, 5L)));
    consumer.consume(messages(7L, List.of(1L, 2L, 3L, 4L, 4L)));

    assertThat(consumer.topics()).containsExactly(CodeReviewProfileQueueContracts.TOPIC);
    assertThat(consumer.policy().batchSize()).isEqualTo(CodeReviewProfileConsumerConstants.BATCH_SIZE);
    assertThat(facts.calls).hasSize(1);
    assertThat(updates.calls).hasSize(1);
    assertThat(updates.calls.get(0)).extracting(CodeReviewProfileFact::reviewId)
        .containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(metrics.results).extracting(CodeReviewProfileUpdateResult::status)
        .containsExactly(CodeReviewProfileUpdateResult.Status.NO_CHANGE);
    assertThat(metrics.callbackFailures).isEqualTo(1);
  }

  @Test
  void rejectsOutOfScopeStructuredOutputAsOneWholeBatch() throws Exception {
    CodeReviewProfileStructuredOutputMapper mapper = new CodeReviewProfileStructuredOutputMapper();
    List<CodeReviewProfilePromptBuilder.Candidate> candidates = List.of(
        candidate(LearnerProfileIdentity.dimension(7L, LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.PROBLEM_SOLVING_APPROACH)),
        candidate(LearnerProfileIdentity.dimension(7L, LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN)),
        candidate(LearnerProfileIdentity.tagAssessment(7L, 9L)));

    var output = objectMapper.readTree("""
        {"generalObservations":[
          {"dimension":"PROBLEM_SOLVING_APPROACH","action":"NO_CHANGE","content":"","reason":"same"},
          {"dimension":"IMPLEMENTATION_AND_ERROR_PATTERN","action":"NO_CHANGE","content":"","reason":"same"}
        ],"tagAssessments":[
          {"tagId":10,"action":"REPLACE","content":"unexpected","reason":"invalid"}
        ]}
        """);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapper.map(output, candidates))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private List<QueueMessage> messages(long userId, List<Long> reviewIds) {
    List<QueueMessage> messages = new ArrayList<>();
    for (int index = 0; index < reviewIds.size(); index++) {
      messages.add(new QueueMessage(
          index + 1L,
          CodeReviewProfileQueueContracts.TOPIC,
          CodeReviewProfileQueueContracts.keyForUser(userId),
          "{\"reviewId\":" + reviewIds.get(index) + "}",
          Instant.EPOCH));
    }
    return messages;
  }

  private static CodeReviewProfilePromptBuilder.Candidate candidate(LearnerProfileIdentity identity) {
    return new CodeReviewProfilePromptBuilder.Candidate(LearnerProfileSnapshot.from(identity, null), "");
  }

  private static CodeReviewProfileFact fact(long reviewId) {
    BigDecimal one = BigDecimal.ONE;
    return new CodeReviewProfileFact(
        reviewId, "problem-" + reviewId, 1, one, one, one, one, one, one, false,
        List.of(), List.of(), List.of(), Instant.EPOCH);
  }

  private static final class RecordingFactRepository implements CodeReviewProfileFactRepository {
    private final List<List<Long>> calls = new ArrayList<>();

    @Override
    public List<CodeReviewProfileFact> findByReviewIds(long userId, List<Long> reviewIds) {
      calls.add(List.copyOf(reviewIds));
      return reviewIds.stream().map(CodeReviewProfileBatchConsumerTest::fact).toList();
    }

    @Override
    public List<CodeReviewProfileFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs) {
      return List.of();
    }

    @Override
    public List<CodeReviewProfileFact> findRecentDistinctProblems(long userId, List<String> excluded, int limit) {
      return List.of();
    }
  }

  private static final class RecordingUpdateService extends CodeReviewProfileUpdateService {
    private final List<List<CodeReviewProfileFact>> calls = new ArrayList<>();

    private RecordingUpdateService() {
      super(null, null, null, null, null, null, 1);
    }

    @Override
    public CodeReviewProfileUpdateResult update(long userId, List<CodeReviewProfileFact> batchFacts) {
      calls.add(List.copyOf(batchFacts));
      return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.NO_CHANGE, 1, 0);
    }
  }

  private static final class RecordingMetrics implements CodeReviewProfileMetrics {
    private final List<CodeReviewProfileUpdateResult> results = new ArrayList<>();
    private int callbackFailures;

    @Override
    public void recordResult(CodeReviewProfileUpdateResult result, Duration duration) {
      results.add(result);
    }

    @Override
    public void recordCallbackFailure(Duration duration) {
      callbackFailures++;
    }
  }
}
