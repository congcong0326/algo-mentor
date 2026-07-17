package org.congcong.algomentor.api.problem.repository;

import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemSeedTag;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;

/**
 * 写入规范化器生成的标签目录和题目关联。
 */
public interface ProblemTagRepository {

  void upsertCatalog(List<ProblemTagDefinition> catalog);

  void replaceAssignments(String problemSlug, List<ProblemSeedTag> tags);
}
