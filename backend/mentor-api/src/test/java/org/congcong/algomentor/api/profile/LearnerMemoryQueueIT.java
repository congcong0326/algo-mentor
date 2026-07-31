package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewQueueContracts;
import org.junit.jupiter.api.Test;

class LearnerMemoryQueueIT extends PostgresIntegrationTestSupport {

  private static final String LEGACY_CODE_REVIEW_TOPIC = "learner-profile.code-review.v1";

  @Test
  void removesPendingAndSucceededLegacyMessagesWithoutTouchingV2Messages() throws Exception {
    migrateTo("46");
    insertMessage(LEGACY_CODE_REVIEW_TOPIC, "PENDING");
    insertMessage(LEGACY_CODE_REVIEW_TOPIC, "SUCCEEDED");
    insertMessage(LearnerMemoryCodeReviewQueueContracts.TOPIC, "PENDING");
    insertMessage(LearnerMemoryCodeReviewQueueContracts.TOPIC, "SUCCEEDED");

    migrateLatest();

    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ?", LEGACY_CODE_REVIEW_TOPIC))
        .isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ?", LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .isEqualTo(2L);
  }

  private void insertMessage(String topic, String status) throws Exception {
    execute(
        """
        INSERT INTO queue_message (topic, message_key, message_value, status, succeeded_at)
        VALUES (?, '1', '{"reviewId":1}', ?, CASE WHEN ? = 'SUCCEEDED' THEN NOW() ELSE NULL END)
        """,
        topic, status, status);
  }
}
