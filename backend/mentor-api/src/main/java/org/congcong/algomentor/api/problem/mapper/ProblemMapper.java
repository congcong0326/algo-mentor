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
      @Param("category") String category
  );

  List<ProblemRow> findProblems(
      @Param("keyword") String keyword,
      @Param("difficulty") String difficulty,
      @Param("tag") String tag,
      @Param("category") String category,
      @Param("sort") String sort,
      @Param("limit") int limit,
      @Param("offset") int offset
  );

  ProblemRow findProblemBySlug(@Param("slug") String slug);

  long countAllProblems();

  List<ProblemFilterCountRow> countProblemsByDifficulty();

  List<ProblemFilterCountRow> countProblemsByTag();

  List<ProblemCategoryFilterRow> countProblemCategories();

  int upsertProblem(ProblemUpsertRow row);
}
