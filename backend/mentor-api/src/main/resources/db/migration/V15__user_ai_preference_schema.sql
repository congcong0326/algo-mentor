CREATE TABLE IF NOT EXISTS user_ai_preference (
  user_id BIGINT PRIMARY KEY REFERENCES auth_users(id) ON DELETE CASCADE,
  coach_style VARCHAR(64) NOT NULL DEFAULT 'SOCRATIC_GUIDE',
  response_language VARCHAR(32) NOT NULL DEFAULT 'ZH_CN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_user_ai_preference_coach_style CHECK (
    coach_style IN (
      'SOCRATIC_GUIDE',
      'DIRECT_EXPLAINER',
      'INTERVIEWER',
      'STRICT_REVIEWER',
      'SUPPORTIVE_MENTOR'
    )
  ),
  CONSTRAINT ck_user_ai_preference_response_language CHECK (
    response_language IN ('ZH_CN', 'EN_US')
  )
);
