ALTER TABLE learner_memory_claim_revision
  DROP CONSTRAINT ck_memory_claim_pattern;

ALTER TABLE learner_memory_claim_revision
  ADD CONSTRAINT ck_memory_claim_pattern CHECK (
    evidence_pattern IN (
      'USER_DECLARATION',
      'USER_CORRECTION',
      'SINGLE_REVIEW',
      'SAME_PROBLEM_PERSISTENCE',
      'SAME_PROBLEM_RECOVERY',
      'SAME_PROBLEM_REGRESSION',
      'CROSS_PROBLEM_RECOVERY',
      'CROSS_PROBLEM_RECURRENCE',
      'CROSS_PROBLEM_LONGITUDINAL',
      'TAG_BREADTH'
    )
  );
