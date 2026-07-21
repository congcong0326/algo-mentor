package org.congcong.algomentor.api.practice.service;

import java.util.List;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;

/** 规范化题目标签到 Review 应用端口的适配。 */
public class MyBatisTrustedProblemTagCatalog implements TrustedProblemTagCatalog {

  private final ProblemTagMapper mapper;

  public MyBatisTrustedProblemTagCatalog(ProblemTagMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<TrustedProblemTag> findByProblemSlug(String problemSlug) {
    if (problemSlug == null || problemSlug.isBlank()) {
      return List.of();
    }
    return mapper.findTrustedByProblemSlug(problemSlug.trim()).stream()
        .map(row -> new TrustedProblemTag(row.tagId(), row.value(), row.labelEn(), row.labelZh())).toList();
  }
}
