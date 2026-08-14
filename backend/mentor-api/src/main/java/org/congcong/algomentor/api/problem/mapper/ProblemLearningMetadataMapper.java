package org.congcong.algomentor.api.problem.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryItemUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCodeTemplateUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemHintUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemMetadataImportRunRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRelationUpsertRow;

/** 元数据导入专用 SQL 边界；不向业务读取层泄漏。 */
@Mapper
public interface ProblemLearningMetadataMapper {

  List<String> findExistingProblemSlugs(@Param("slugs") List<String> slugs);

  int deleteRelationsForSourceProblem(@Param("problemSlug") String problemSlug, @Param("source") String source);

  int deleteHintsForProblemSourceSite(@Param("problemSlug") String problemSlug, @Param("sourceSite") String sourceSite);

  int deleteCodeTemplatesForProblem(@Param("problemSlug") String problemSlug);

  int deleteCategoryItemsForProblemSource(@Param("problemSlug") String problemSlug, @Param("source") String source);

  int upsertRelation(ProblemRelationUpsertRow row);

  int upsertHint(ProblemHintUpsertRow row);

  int upsertCodeTemplate(ProblemCodeTemplateUpsertRow row);

  Long upsertCategory(ProblemCategoryUpsertRow row);

  int upsertCategoryItem(ProblemCategoryItemUpsertRow row);

  int insertImportRun(ProblemMetadataImportRunRow row);
}
