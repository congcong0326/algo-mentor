-- P0-3.1 教练风格收敛为 GUIDED/DIRECT 两档。
-- 项目未上线，存量偏好统一重置为默认 GUIDED。

ALTER TABLE user_ai_preference
  DROP CONSTRAINT IF EXISTS ck_user_ai_preference_coach_style;

UPDATE user_ai_preference SET coach_style = 'GUIDED';

ALTER TABLE user_ai_preference
  ALTER COLUMN coach_style SET DEFAULT 'GUIDED';

ALTER TABLE user_ai_preference
  ADD CONSTRAINT ck_user_ai_preference_coach_style CHECK (
    coach_style IN ('GUIDED', 'DIRECT')
  );
