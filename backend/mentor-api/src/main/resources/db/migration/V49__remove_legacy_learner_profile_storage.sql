DELETE FROM queue_message
WHERE topic = 'learner-profile.code-review.v1';

DROP TABLE IF EXISTS learner_profile_entry;
