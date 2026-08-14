package org.congcong.algomentor.api.problem.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.api.problem.mapper.ProblemLearningMetadataMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryItemUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCategoryUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCodeTemplateUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCodeTemplateRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemHintUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemMetadataImportRunRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRelationUpsertRow;
import org.congcong.algomentor.api.problem.model.ProblemCategoryItemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCategorySeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCodeTemplateSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemHintSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemLearningMetadataContract;
import org.congcong.algomentor.api.problem.model.ProblemRelationSeedRecord;

public class MyBatisProblemLearningMetadataRepository implements ProblemLearningMetadataRepository {

  private final ProblemLearningMetadataMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisProblemLearningMetadataRepository(
      ProblemLearningMetadataMapper mapper,
      ObjectMapper objectMapper
  ) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<String> findExistingProblemSlugs(List<String> slugs) {
    if (slugs.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(new LinkedHashSet<>(mapper.findExistingProblemSlugs(slugs)));
  }

  @Override
  public List<ProblemCodeTemplateSeedRecord> findCodeTemplatesByProblemSlug(String problemSlug) {
    if (problemSlug == null || problemSlug.isBlank()) {
      return List.of();
    }
    return mapper.findCodeTemplatesByProblemSlug(problemSlug.trim()).stream()
        .map(row -> new ProblemCodeTemplateSeedRecord(
            problemSlug.trim(),
            row.languageSlug(),
            row.languageLabel(),
            row.code(),
            row.sourceSite(),
            row.sourceSnapshot()))
        .toList();
  }

  @Override
  public void replaceLeetCodeRelations(
      List<String> sourceProblemSlugs,
      List<ProblemRelationSeedRecord> relations
  ) {
    sourceProblemSlugs.forEach(slug -> mapper.deleteRelationsForSourceProblem(
        slug, ProblemLearningMetadataContract.SOURCE_LEETCODE));
    relations.forEach(record -> mapper.upsertRelation(new ProblemRelationUpsertRow(
        record.sourceSlug(), record.targetSlug(), record.relationType(), record.source(),
        record.sourceSnapshot(), json(record.metadata()))));
  }

  @Override
  public void replaceHints(
      List<String> sourceProblemSlugs,
      List<ProblemHintSeedRecord> hints,
      Map<String, List<String>> sourceSitesByProblem
  ) {
    for (String slug : sourceProblemSlugs) {
      sourceSitesByProblem.getOrDefault(slug, List.of())
          .forEach(site -> mapper.deleteHintsForProblemSourceSite(slug, site));
    }
    hints.forEach(record -> mapper.upsertHint(new ProblemHintUpsertRow(
        record.problemSlug(), record.sourceSite(), record.ordinal(), record.contentMarkdown(),
        record.sourceSnapshot())));
  }

  @Override
  public void replaceCodeTemplates(
      List<String> sourceProblemSlugs,
      List<ProblemCodeTemplateSeedRecord> templates
  ) {
    sourceProblemSlugs.forEach(mapper::deleteCodeTemplatesForProblem);
    templates.forEach(record -> mapper.upsertCodeTemplate(new ProblemCodeTemplateUpsertRow(
        record.problemSlug(), record.languageSlug(), record.languageLabel(), record.code(),
        record.sourceSite(), record.sourceSnapshot())));
  }

  @Override
  public void replaceLeetCodeCategoryItems(
      List<String> sourceProblemSlugs,
      List<ProblemCategorySeedRecord> categories,
      List<ProblemCategoryItemSeedRecord> items
  ) {
    categories.forEach(record -> mapper.upsertCategory(new ProblemCategoryUpsertRow(
        record.slug(), record.nameEn(), record.nameZh(), record.source(), record.sourceSnapshot())));
    sourceProblemSlugs.forEach(slug -> mapper.deleteCategoryItemsForProblemSource(
        slug, ProblemLearningMetadataContract.SOURCE_LEETCODE));
    items.forEach(record -> mapper.upsertCategoryItem(new ProblemCategoryItemUpsertRow(
        record.problemSlug(), record.categorySlug(), record.source(), record.sourceSnapshot())));
  }

  @Override
  public void insertImportRun(
      String manifestPath,
      String manifestSha256,
      String sourceSnapshot,
      int readCount,
      int matchedCount,
      int skippedCount,
      String auditReportJson
  ) {
    mapper.insertImportRun(new ProblemMetadataImportRunRow(
        manifestPath, manifestSha256, sourceSnapshot, readCount, matchedCount, skippedCount, 0,
        auditReportJson));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Unable to serialize problem metadata JSON.", exception);
    }
  }
}
