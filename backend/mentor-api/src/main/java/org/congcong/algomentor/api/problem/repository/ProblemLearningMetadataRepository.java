package org.congcong.algomentor.api.problem.repository;

import java.util.List;
import java.util.Set;
import org.congcong.algomentor.api.problem.model.ProblemCategoryItemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCategorySeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCodeTemplateSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemHintSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemRelationSeedRecord;

/** 学习元数据导入所需的唯一持久化端口。 */
public interface ProblemLearningMetadataRepository {

  Set<String> findExistingProblemSlugs(List<String> slugs);

  void replaceLeetCodeRelations(List<String> sourceProblemSlugs, List<ProblemRelationSeedRecord> relations);

  void replaceHints(List<String> sourceProblemSlugs, List<ProblemHintSeedRecord> hints,
                    java.util.Map<String, List<String>> sourceSitesByProblem);

  void replaceCodeTemplates(List<String> sourceProblemSlugs, List<ProblemCodeTemplateSeedRecord> templates);

  void replaceLeetCodeCategoryItems(List<String> sourceProblemSlugs,
                                    List<ProblemCategorySeedRecord> categories,
                                    List<ProblemCategoryItemSeedRecord> items);

  void insertImportRun(
      String manifestPath,
      String manifestSha256,
      String sourceSnapshot,
      int readCount,
      int matchedCount,
      int skippedCount,
      String auditReportJson
  );
}
