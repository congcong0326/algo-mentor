DELETE FROM generic_policy
WHERE type_code IN (
  'ai.system-prompt.mentor-conversation.v1',
  'ai.system-prompt.topic-explanation.v1'
);
