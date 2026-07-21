CREATE TABLE practice_code_review_tag (
  review_id BIGINT NOT NULL REFERENCES practice_code_review(id) ON DELETE CASCADE,
  tag_id BIGINT NOT NULL REFERENCES problem_tag(id) ON DELETE RESTRICT,
  PRIMARY KEY (review_id, tag_id)
);
CREATE INDEX idx_practice_code_review_tag_tag_review ON practice_code_review_tag (tag_id, review_id);
