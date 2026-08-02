ALTER TABLE practice_code_review
  ADD COLUMN content_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN';

ALTER TABLE practice_code_review
  ADD CONSTRAINT ck_practice_code_review_content_locale
  CHECK (content_locale IN ('zh-CN', 'en-US'));
