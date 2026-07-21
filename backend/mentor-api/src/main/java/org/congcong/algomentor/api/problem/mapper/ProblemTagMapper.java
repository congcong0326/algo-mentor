package org.congcong.algomentor.api.problem.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagAssignmentRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagCatalogUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.TrustedProblemTagRow;

@Mapper
public interface ProblemTagMapper {

  int upsertCatalog(@Param("rows") List<ProblemTagCatalogUpsertRow> rows);

  int deleteAssignmentsByProblemSlug(@Param("problemSlug") String problemSlug);

  int insertAssignments(@Param("rows") List<ProblemTagAssignmentRow> rows);

  List<String> findArrayConsistencyViolationSlugs();

  List<String> findAssignmentOrdinalViolationSlugs();

  List<String> findDanglingAssignmentReferences();

  List<String> findDuplicateCatalogValues();

  List<String> findInactiveCatalogValues(@Param("values") List<String> values);

  List<TrustedProblemTagRow> findTrustedByProblemSlug(@Param("problemSlug") String problemSlug);
}
