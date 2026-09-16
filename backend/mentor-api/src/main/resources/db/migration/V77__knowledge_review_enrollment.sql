-- 独立管理复习加入状态，移除复习不删除学习记录。
ALTER TABLE knowledge_card_user_state
  ADD COLUMN review_enrolled BOOLEAN NOT NULL DEFAULT TRUE,
  ALTER COLUMN last_rating DROP NOT NULL,
  ALTER COLUMN last_reviewed_at DROP NOT NULL;

COMMENT ON COLUMN knowledge_card_user_state.review_enrolled IS '是否加入复习；移除后保留调度和评级流水，再次加入恢复进度';
COMMENT ON TABLE knowledge_card_user_state IS '手动加入或首次评价时创建；未评级时 last_rating 和 last_reviewed_at 为空';
