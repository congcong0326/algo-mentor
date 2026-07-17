package org.congcong.algomentor.api.problem.model;

import java.util.List;

/**
 * 数据库写入前已完成标签规范化的题目 seed。
 */
public record NormalizedProblemSeed(
    ProblemSeedRecord problem,
    List<ProblemSeedTag> tags
) {

  public NormalizedProblemSeed {
    tags = List.copyOf(tags);
  }
}
