package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import java.util.List;

/** 学习计划修订 Tool 的稳定名称、可信 metadata key 和状态值。 */
public final class LearningPlanRevisionToolContracts {

  public static final String QUERY_TOOL_NAME = "query_learning_plan_revision";
  public static final String COMPILE_TOOL_NAME = "compile_learning_plan_revision";

  public static final String METADATA_SCENARIO = "learningPlanRevisionScenario";
  public static final String METADATA_REVISION_ID = "learningPlanRevisionId";
  public static final String SCENARIO = "LEARNING_PLAN_REVISION";

  public static final String PROJECTION_INLINE_FULL = "INLINE_FULL";
  public static final String PROJECTION_SUMMARY_WITH_TOOLS = "SUMMARY_WITH_TOOLS";

  public static final String STATUS_PASS = "PASS";
  public static final String STATUS_NEEDS_REVISION = "NEEDS_REVISION";
  public static final String FINAL_STATUS_COMPILED = "COMPILED";

  public static final String BASELINE_CURRENT_REVISION = "CURRENT_REVISION";
  public static final String BASELINE_ORIGINAL_DRAFT = "ORIGINAL_DRAFT";
  public static final String BASELINE_PREVIOUS_REVISION = "PREVIOUS_REVISION";

  public static final String OPERATION_READ_BASELINE_OPTIONS = "READ_BASELINE_OPTIONS";
  public static final String OPERATION_READ_PHASE = "READ_PHASE";
  public static final String OPERATION_FIND_PROBLEMS = "FIND_PROBLEMS";

  public static final List<String> AGENT_TOOLS = List.of(QUERY_TOOL_NAME, COMPILE_TOOL_NAME);

  private LearningPlanRevisionToolContracts() {
  }

  public static String artifactRef(long revisionId) {
    return "draft-revision:" + revisionId + ":compiled";
  }
}
