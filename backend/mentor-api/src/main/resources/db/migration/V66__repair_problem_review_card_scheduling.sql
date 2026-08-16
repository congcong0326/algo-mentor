-- 旧版 REVIEW_PASSED -> REVIEW_FAILED 转换会保留高分稳定度，却没有实际复习时间。
-- 这会让 FSRS 在重学成功后计算出错误的多年间隔，必须重置为新学习卡。
UPDATE problem_review_card
SET repetitions = 0,
    interval_days = 0,
    fsrs_state = 'LEARNING',
    fsrs_step = 0,
    fsrs_stability = NULL,
    fsrs_difficulty = NULL,
    due_at = NOW(),
    last_reviewed_at = NULL,
    last_rating = NULL,
    updated_at = NOW()
WHERE fsrs_state = 'RELEARNING'
  AND last_reviewed_at IS NULL
  AND fsrs_stability IS NOT NULL
  AND fsrs_difficulty IS NOT NULL;

-- 通过的代码 Review 作为一次合成 FSRS 复习；旧数据未保存其时间，
-- 因此将调度状态锚定到已存间隔的起点。
UPDATE problem_review_card
SET last_reviewed_at = due_at - make_interval(days => interval_days),
    updated_at = NOW()
WHERE fsrs_state = 'REVIEW'
  AND last_reviewed_at IS NULL
  AND fsrs_stability IS NOT NULL
  AND fsrs_difficulty IS NOT NULL;
