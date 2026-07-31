package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.List;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;

/** 学习计划 Definition 可声明的题库工具稳定名称。 */
public final class LearningPlanAgentToolNames {

  /** 查询本地题库可用过滤项。 */
  public static final String LIST_PROBLEM_FILTERS = "list_problem_filters";

  /** 按过滤条件查询本地题库候选题。 */
  public static final String SEARCH_PROBLEMS = "search_problems";

  /** 在当前 Agent run 内续读被压缩的大型题库工具结果。 */
  public static final String READ_TOOL_RESULT = ReadToolResultTool.NAME;

  /** 草案、修订和扩展 Prompt 所需的最小题库工具白名单。 */
  public static final List<String> PLANNING_TOOLS = List.of(
      LIST_PROBLEM_FILTERS,
      SEARCH_PROBLEMS,
      READ_TOOL_RESULT);

  private LearningPlanAgentToolNames() {
  }
}
