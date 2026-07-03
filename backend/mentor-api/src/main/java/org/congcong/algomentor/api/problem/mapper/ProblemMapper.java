package org.congcong.algomentor.api.problem.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryFilterRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemFilterCountRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemUpsertRow;

@Mapper
public interface ProblemMapper {

  long countProblems(
      @Param("keyword") String keyword,
      @Param("difficulty") String difficulty,
      @Param("tag") String tag,
      @Param("category") String category,
      @Param("company") String company,
      @Param("role") String role,
      @Param("recencyBucket") String recencyBucket
  );

  List<ProblemRow> findProblems(
      @Param("keyword") String keyword,
      @Param("difficulty") String difficulty,
      @Param("tag") String tag,
      @Param("category") String category,
      @Param("company") String company,
      @Param("role") String role,
      @Param("recencyBucket") String recencyBucket,
      @Param("sort") String sort,
      @Param("locale") String locale,
      @Param("limit") int limit,
      @Param("offset") int offset
  );

  ProblemRow findProblemBySlug(@Param("slug") String slug);

  long countAllProblems();

  List<ProblemFilterCountRow> countProblemsByDifficulty();

  List<ProblemFilterCountRow> countProblemsByTag(@Param("locale") String locale);

  List<ProblemCategoryFilterRow> countProblemCategories();

  List<ProblemFilterCountRow> countProblemCompanies();

  List<ProblemFilterCountRow> countProblemSignalRoles();

  List<ProblemFilterCountRow> countProblemSignalRecencyBuckets();

  int clearConflictingFrontendId(@Param("slug") String slug, @Param("frontendId") Integer frontendId);

  int clearConflictingFrontendDisplayId(
      @Param("slug") String slug,
      @Param("frontendDisplayId") String frontendDisplayId
  );

  int upsertProblem(ProblemUpsertRow row);
}
