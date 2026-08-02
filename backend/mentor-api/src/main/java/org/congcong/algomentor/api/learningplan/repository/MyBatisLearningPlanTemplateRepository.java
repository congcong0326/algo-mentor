package org.congcong.algomentor.api.learningplan.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateImportRunRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplatePhaseRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateProblemRefRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateCatalogCategory;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateImportRun;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.invalidation.SpringCacheInvalidationExecutor;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisLearningPlanTemplateRepository implements LearningPlanTemplateRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
  };
  private static final CacheRegionName CATALOG_CACHE_NAME = new CacheRegionName("learning-plan-template-catalog");

  private final LearningPlanTemplateMapper mapper;
  private final ObjectMapper objectMapper;
  private final LocalBoundedCacheRegion<LearningPlanContentLocale, LearningPlanTemplateCatalog> catalogCache;
  private final CacheInvalidationExecutor invalidationExecutor;

  public MyBatisLearningPlanTemplateRepository(
      LearningPlanTemplateMapper mapper,
      ObjectMapper objectMapper
  ) {
    this(mapper, objectMapper, null, null, new SpringCacheInvalidationExecutor());
  }

  public MyBatisLearningPlanTemplateRepository(
      LearningPlanTemplateMapper mapper,
      ObjectMapper objectMapper,
      LocalCacheRegionFactory cacheFactory,
      LearningPlanTemplateCacheProperties cacheProperties
  ) {
    this(mapper, objectMapper, cacheFactory, cacheProperties, new SpringCacheInvalidationExecutor());
  }

  public MyBatisLearningPlanTemplateRepository(
      LearningPlanTemplateMapper mapper,
      ObjectMapper objectMapper,
      LocalCacheRegionFactory cacheFactory,
      LearningPlanTemplateCacheProperties cacheProperties,
      CacheInvalidationExecutor invalidationExecutor
  ) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    catalogCache = cacheFactory == null || cacheProperties == null
        ? null
        : cacheFactory.createBounded(new LocalBoundedCacheSpec(
            CATALOG_CACHE_NAME, cacheProperties.getCatalogMaximumSize()));
    this.invalidationExecutor = invalidationExecutor;
  }

  @Override
  public List<LearningPlanTemplate> findAllTemplates() {
    return findAllTemplates(LearningPlanContentLocale.ZH_CN);
  }

  @Override
  public List<LearningPlanTemplate> findAllTemplates(LearningPlanContentLocale locale) {
    if (catalogCache == null) {
      return mapper.findAllTemplates().stream().map(row -> toTemplate(row, List.of())).toList();
    }
    return catalog(locale).orderedTemplates();
  }

  @Override
  public Optional<LearningPlanTemplate> findByTemplateId(String templateId) {
    return findByTemplateId(templateId, LearningPlanContentLocale.ZH_CN);
  }

  @Override
  public Optional<LearningPlanTemplate> findByTemplateId(String templateId, LearningPlanContentLocale locale) {
    if (catalogCache != null) {
      return Optional.ofNullable(catalog(locale).templatesById().get(templateId));
    }
    return Optional.ofNullable(mapper.findByTemplateId(templateId))
        .map(row -> toTemplate(row, true));
  }

  @Override
  @Transactional
  public LearningPlanTemplate saveTemplate(LearningPlanTemplate template) {
    long templateDbId = mapper.upsertTemplate(toTemplateRow(template));
    mapper.deleteProblemRefsByTemplateId(templateDbId);
    mapper.deletePhasesByTemplateId(templateDbId);
    for (LearningPlanTemplatePhase phase : template.phases()) {
      long phaseDbId = mapper.insertPhase(toPhaseRow(templateDbId, phase));
      for (LearningPlanTemplateProblemRef ref : phase.problemRefs()) {
        mapper.insertProblemRef(toProblemRefRow(templateDbId, phaseDbId, ref));
      }
    }
    LearningPlanTemplate saved = toTemplate(mapper.findByTemplateId(template.templateId()), true);
    scheduleCatalogCacheInvalidation();
    return saved;
  }

  @Override
  public void insertImportRun(LearningPlanTemplateImportRun importRun) {
    mapper.insertImportRun(new LearningPlanTemplateImportRunRow(
        importRun.sourceName(),
        importRun.sourceCommit(),
        importRun.seedPath(),
        importRun.manifestPath(),
        importRun.metadataPath(),
        importRun.checksum(),
        importRun.templateCount(),
        importRun.problemRefCount(),
        importRun.matchedProblemCount(),
        importRun.missingProblemCount(),
        importRun.errorCount(),
        json(importRun.metadata())));
  }

  private LearningPlanTemplate toTemplate(LearningPlanTemplateRow row, boolean includeDetails) {
    return toTemplate(row, includeDetails ? loadPhases(row.id()) : List.of());
  }

  private LearningPlanTemplate toTemplate(LearningPlanTemplateRow row, List<LearningPlanTemplatePhase> phases) {
    return new LearningPlanTemplate(
        row.id(),
        row.templateId(),
        row.title(),
        row.titleEn(),
        row.summary(),
        row.summaryEn(),
        LearningPlanTemplateCatalogCategory.valueOf(row.catalogCategory()),
        row.recommendedOrder(),
        LearningPlanIntent.valueOf(row.intent()),
        row.goal(),
        row.goalEn(),
        value(row.defaultDurationWeeks()),
        LearningPlanLevel.valueOf(row.level()),
        value(row.defaultWeeklyHours()),
        row.programmingLanguage(),
        LearningPlanDifficultyPreference.valueOf(row.difficultyPreference()),
        Boolean.TRUE.equals(row.interviewOriented()),
        read(row.topicPreferencesJson(), STRING_LIST),
        row.targetAudience(),
        row.targetAudienceEn(),
        read(row.difficultyMixJson(), OBJECT_MAP),
        read(row.prerequisitesJson(), STRING_LIST),
        readNullableList(row.prerequisitesEnJson()),
        read(row.recommendedForJson(), STRING_LIST),
        readNullableList(row.recommendedForEnJson()),
        read(row.notRecommendedForJson(), STRING_LIST),
        readNullableList(row.notRecommendedForEnJson()),
        row.expectedOutcome(),
        row.expectedOutcomeEn(),
        Boolean.TRUE.equals(row.englishContentReady()),
        row.sourceName(),
        row.sourceUrl(),
        row.sourceCommit(),
        row.sourceDataPath(),
        row.sourceDescription(),
        row.curationNotes(),
        row.licenseNotice(),
        value(row.problemCount()),
        value(row.matchedProblemCount()),
        value(row.missingProblemCount()),
        read(row.metadataJson(), OBJECT_MAP),
        phases);
  }

  private List<LearningPlanTemplatePhase> loadPhases(long templateDbId) {
    return toPhases(
        mapper.findPhasesByTemplateDbId(templateDbId),
        mapper.findProblemRefsByTemplateDbId(templateDbId));
  }

  private List<LearningPlanTemplatePhase> toPhases(
      List<LearningPlanTemplatePhaseRow> phaseRows,
      List<LearningPlanTemplateProblemRefRow> refRows
  ) {
    Map<Integer, List<LearningPlanTemplateProblemRef>> refsByPhase = new LinkedHashMap<>();
    for (LearningPlanTemplateProblemRefRow refRow : refRows) {
      refsByPhase.computeIfAbsent(value(refRow.phaseIndex()), ignored -> new ArrayList<>())
          .add(toProblemRef(refRow));
    }
    return phaseRows.stream()
        .map(row -> toPhase(row, refsByPhase.getOrDefault(value(row.phaseIndex()), List.of())))
        .toList();
  }

  private LearningPlanTemplateCatalog catalog(LearningPlanContentLocale locale) {
    LearningPlanContentLocale cacheLocale = locale == null ? LearningPlanContentLocale.ZH_CN : locale;
    return catalogCache.get(cacheLocale, ignored -> loadCatalog());
  }

  private LearningPlanTemplateCatalog loadCatalog() {
    List<LearningPlanTemplateRow> templateRows = mapper.findAllTemplates();
    Map<Long, List<LearningPlanTemplatePhaseRow>> phasesByTemplate = mapper.findAllPhases().stream()
        .collect(java.util.stream.Collectors.groupingBy(
            LearningPlanTemplatePhaseRow::templateDbId,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()));
    Map<Long, List<LearningPlanTemplateProblemRefRow>> refsByTemplate = mapper.findAllProblemRefs().stream()
        .collect(java.util.stream.Collectors.groupingBy(
            LearningPlanTemplateProblemRefRow::templateDbId,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()));
    List<LearningPlanTemplate> templates = templateRows.stream()
        .map(row -> toTemplate(
            row,
            toPhases(
                phasesByTemplate.getOrDefault(row.id(), List.of()),
                refsByTemplate.getOrDefault(row.id(), List.of()))))
        .toList();
    Map<String, LearningPlanTemplate> byId = templates.stream().collect(java.util.stream.Collectors.toMap(
        LearningPlanTemplate::templateId,
        Function.identity(),
        (left, right) -> left,
        LinkedHashMap::new));
    return new LearningPlanTemplateCatalog(templates, byId);
  }

  void scheduleCatalogCacheInvalidation() {
    if (catalogCache != null) {
      invalidationExecutor.afterCommit(catalogCache::invalidateAll);
    }
  }

  private LearningPlanTemplatePhase toPhase(
      LearningPlanTemplatePhaseRow row,
      List<LearningPlanTemplateProblemRef> refs
  ) {
    return new LearningPlanTemplatePhase(
        row.id(),
        value(row.phaseIndex()),
        row.title(),
        row.titleEn(),
        value(row.durationWeeks()),
        row.focus(),
        row.focusEn(),
        read(row.objectivesJson(), STRING_LIST),
        readNullableList(row.objectivesEnJson()),
        read(row.recommendedTagsJson(), STRING_LIST),
        read(row.acceptanceCriteriaJson(), STRING_LIST),
        readNullableList(row.acceptanceCriteriaEnJson()),
        row.reviewAdvice(),
        row.reviewAdviceEn(),
        refs);
  }

  private LearningPlanTemplateProblemRef toProblemRef(LearningPlanTemplateProblemRefRow row) {
    return new LearningPlanTemplateProblemRef(
        row.id(),
        value(row.phaseIndex()),
        value(row.sortOrder()),
        value(row.sourceOrder()),
        row.problemSlug(),
        row.sourceTitle(),
        row.sourceDifficulty(),
        row.pattern(),
        row.sourceUrl(),
        Boolean.TRUE.equals(row.matchedProblem()),
        read(row.metadataJson(), OBJECT_MAP));
  }

  private LearningPlanTemplateRow toTemplateRow(LearningPlanTemplate template) {
    return new LearningPlanTemplateRow(
        template.id(),
        template.templateId(),
        template.title(),
        template.titleEn(),
        template.summary(),
        template.summaryEn(),
        template.catalogCategory().name(),
        template.recommendedOrder(),
        template.intent().name(),
        template.goal(),
        template.goalEn(),
        template.defaultDurationWeeks(),
        template.level().name(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference().name(),
        template.interviewOriented(),
        json(template.topicPreferences()),
        template.targetAudience(),
        template.targetAudienceEn(),
        json(template.difficultyMix()),
        json(template.prerequisites()),
        json(template.prerequisitesEn()),
        json(template.recommendedFor()),
        json(template.recommendedForEn()),
        json(template.notRecommendedFor()),
        json(template.notRecommendedForEn()),
        template.expectedOutcome(),
        template.expectedOutcomeEn(),
        template.englishContentReady(),
        template.sourceName(),
        template.sourceUrl(),
        template.sourceCommit(),
        template.sourceDataPath(),
        template.sourceDescription(),
        template.curationNotes(),
        template.licenseNotice(),
        template.problemCount(),
        template.matchedProblemCount(),
        template.missingProblemCount(),
        json(template.metadata()),
        null,
        null);
  }

  private LearningPlanTemplatePhaseRow toPhaseRow(long templateDbId, LearningPlanTemplatePhase phase) {
    return new LearningPlanTemplatePhaseRow(
        phase.id(),
        templateDbId,
        phase.phaseIndex(),
        phase.title(),
        phase.titleEn(),
        phase.durationWeeks(),
        phase.focus(),
        phase.focusEn(),
        json(phase.objectives()),
        json(phase.objectivesEn()),
        json(phase.recommendedTags()),
        json(phase.acceptanceCriteria()),
        json(phase.acceptanceCriteriaEn()),
        phase.reviewAdvice(),
        phase.reviewAdviceEn());
  }

  private LearningPlanTemplateProblemRefRow toProblemRefRow(
      long templateDbId,
      long phaseDbId,
      LearningPlanTemplateProblemRef ref
  ) {
    return new LearningPlanTemplateProblemRefRow(
        ref.id(),
        templateDbId,
        phaseDbId,
        ref.phaseIndex(),
        ref.sortOrder(),
        ref.sourceOrder(),
        ref.problemSlug(),
        ref.sourceTitle(),
        ref.sourceDifficulty(),
        ref.pattern(),
        ref.sourceUrl(),
        ref.matchedProblem(),
        json(ref.metadata()));
  }

  private JsonNode json(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  private <T> T read(JsonNode node, TypeReference<T> type) {
    try {
      return objectMapper.readerFor(type).readValue(node);
    } catch (IOException exception) {
      throw new LearningPlanException("LEARNING_PLAN_TEMPLATE_JSON_INVALID", "学习计划模板 JSON 解析失败。");
    }
  }

  private List<String> readNullableList(JsonNode node) {
    return node == null || node.isNull() ? List.of() : read(node, STRING_LIST);
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
