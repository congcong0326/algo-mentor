ALTER TABLE learning_plan_template
  ADD COLUMN catalog_category VARCHAR(40);

ALTER TABLE learning_plan_template
  ADD COLUMN recommended_order INTEGER;

UPDATE learning_plan_template
SET catalog_category = CASE template_id
  WHEN 'programming_skills_implementation_foundation' THEN 'SYSTEMATIC_LEARNING'
  WHEN 'cn_algorithm_foundation_12weeks' THEN 'SYSTEMATIC_LEARNING'
  WHEN 'carl_algorithm_roadmap_full' THEN 'SYSTEMATIC_LEARNING'
  WHEN 'leetcode_patterns_beginner_roadmap' THEN 'SYSTEMATIC_LEARNING'
  WHEN 'labuladong_algo_thinking' THEN 'SYSTEMATIC_LEARNING'
  WHEN 'neetcode_150_systematic_interview' THEN 'INTERVIEW_PREP'
  WHEN 'neetcode_blind_75_interview_core' THEN 'INTERVIEW_PREP'
  WHEN 'tih_best_practice_50_5weeks' THEN 'INTERVIEW_PREP'
  WHEN 'sword_offer_classic' THEN 'INTERVIEW_PREP'
  WHEN 'cracking_coding_interview_classic' THEN 'INTERVIEW_PREP'
  WHEN 'leetcode_75_core_sprint' THEN 'INTERVIEW_PREP'
  WHEN 'leetcode_top_interview_150' THEN 'INTERVIEW_PREP'
  WHEN 'leetcode_top_100_liked_revision' THEN 'INTERVIEW_PREP'
  WHEN 'tih_algorithm_essentials' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_dynamic_programming_foundation' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_dp_advanced' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_graph_bfs_dfs' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_binary_search_boundaries' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_sliding_window_two_pointers' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_tree_binary_tree_foundation' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_backtracking_foundation' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_heap_priority_queue' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_greedy_strategies' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_stack_monotonic' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_bit_manipulation' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_linked_list' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_union_find_and_advanced_graph' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_prefix_sum_difference' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_trie_and_string_advanced' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_intervals_scheduling' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'topic_data_structure_design' THEN 'TOPIC_BREAKTHROUGH'
  WHEN 'leetcode_sql_50' THEN 'LANGUAGE_AND_ROLE'
  WHEN 'leetcode_javascript_30_days' THEN 'LANGUAGE_AND_ROLE'
  WHEN 'leetcode_pandas_introduction' THEN 'LANGUAGE_AND_ROLE'
  WHEN 'leetcode_pandas_30_days' THEN 'LANGUAGE_AND_ROLE'
END,
recommended_order = CASE template_id
  WHEN 'leetcode_75_core_sprint' THEN 1
  WHEN 'cn_algorithm_foundation_12weeks' THEN 2
  WHEN 'carl_algorithm_roadmap_full' THEN 3
  WHEN 'leetcode_top_interview_150' THEN 4
  WHEN 'labuladong_algo_thinking' THEN 5
  WHEN 'topic_dynamic_programming_foundation' THEN 6
END;

ALTER TABLE learning_plan_template
  ALTER COLUMN catalog_category SET NOT NULL;

ALTER TABLE learning_plan_template
  ADD CONSTRAINT ck_learning_plan_template_recommended_order
  CHECK (recommended_order IS NULL OR recommended_order > 0);

CREATE INDEX idx_learning_plan_template_catalog
  ON learning_plan_template(catalog_category, recommended_order, default_duration_weeks, template_id);
