package org.congcong.algomentor.api.problem.repository;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagAssignmentRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagCatalogUpsertRow;
import org.congcong.algomentor.api.problem.model.ProblemSeedTag;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;

public class MyBatisProblemTagRepository implements ProblemTagRepository {

  private final ProblemTagMapper mapper;

  public MyBatisProblemTagRepository(ProblemTagMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void upsertCatalog(List<ProblemTagDefinition> catalog) {
    Objects.requireNonNull(catalog, "catalog must not be null");
    if (catalog.isEmpty()) {
      return;
    }
    List<ProblemTagCatalogUpsertRow> rows = catalog.stream()
        .map(tag -> new ProblemTagCatalogUpsertRow(tag.value(), tag.labelEn(), tag.labelZh()))
        .toList();
    mapper.upsertCatalog(rows);
  }

  @Override
  public void replaceAssignments(String problemSlug, List<ProblemSeedTag> tags) {
    Objects.requireNonNull(problemSlug, "problemSlug must not be null");
    Objects.requireNonNull(tags, "tags must not be null");
    mapper.deleteAssignmentsByProblemSlug(problemSlug);
    if (tags.isEmpty()) {
      return;
    }
    List<ProblemTagAssignmentRow> rows = tags.stream()
        .map(tag -> new ProblemTagAssignmentRow(problemSlug, tag.value(), tag.ordinal()))
        .toList();
    int inserted = mapper.insertAssignments(rows);
    if (inserted != rows.size()) {
      throw new IllegalStateException(
          "Failed to replace all problem tag assignments: slug=" + problemSlug);
    }
  }
}
