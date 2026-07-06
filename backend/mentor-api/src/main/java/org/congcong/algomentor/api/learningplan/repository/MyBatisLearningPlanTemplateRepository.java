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
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateImportRunRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplatePhaseRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateProblemRefRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateImportRun;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisLearningPlanTemplateRepository implements LearningPlanTemplateRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
  };

  private final LearningPlanTemplateMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisLearningPlanTemplateRepository(
      LearningPlanTemplateMapper mapper,
      ObjectMapper objectMapper
  ) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<LearningPlanTemplate> findAllTemplates() {
    return mapper.findAllTemplates().stream()
        .map(row -> toTemplate(row, false))
        .toList();
  }

  @Override
  public Optional<LearningPlanTemplate> findByTemplateId(String templateId) {
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
    return toTemplate(mapper.findByTemplateId(template.templateId()), true);
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
    List<LearningPlanTemplatePhase> phases = includeDetails ? loadPhases(row.id()) : List.of();
    return new LearningPlanTemplate(
        row.id(),
        row.templateId(),
        row.title(),
        row.summary(),
        LearningPlanIntent.valueOf(row.intent()),
        row.goal(),
        value(row.defaultDurationWeeks()),
        LearningPlanLevel.valueOf(row.level()),
        value(row.defaultWeeklyHours()),
        row.programmingLanguage(),
        LearningPlanDifficultyPreference.valueOf(row.difficultyPreference()),
        Boolean.TRUE.equals(row.interviewOriented()),
        read(row.topicPreferencesJson(), STRING_LIST),
        row.targetAudience(),
        read(row.difficultyMixJson(), OBJECT_MAP),
        read(row.prerequisitesJson(), STRING_LIST),
        read(row.recommendedForJson(), STRING_LIST),
        read(row.notRecommendedForJson(), STRING_LIST),
        row.expectedOutcome(),
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
    List<LearningPlanTemplateProblemRefRow> refRows = mapper.findProblemRefsByTemplateDbId(templateDbId);
    Map<Integer, List<LearningPlanTemplateProblemRef>> refsByPhase = new LinkedHashMap<>();
    for (LearningPlanTemplateProblemRefRow refRow : refRows) {
      refsByPhase.computeIfAbsent(value(refRow.phaseIndex()), ignored -> new ArrayList<>())
          .add(toProblemRef(refRow));
    }
    return mapper.findPhasesByTemplateDbId(templateDbId).stream()
        .map(row -> toPhase(row, refsByPhase.getOrDefault(value(row.phaseIndex()), List.of())))
        .toList();
  }

  private LearningPlanTemplatePhase toPhase(
      LearningPlanTemplatePhaseRow row,
      List<LearningPlanTemplateProblemRef> refs
  ) {
    return new LearningPlanTemplatePhase(
        row.id(),
        value(row.phaseIndex()),
        row.title(),
        value(row.durationWeeks()),
        row.focus(),
        read(row.objectivesJson(), STRING_LIST),
        read(row.recommendedTagsJson(), STRING_LIST),
        read(row.acceptanceCriteriaJson(), STRING_LIST),
        row.reviewAdvice(),
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
        template.summary(),
        template.intent().name(),
        template.goal(),
        template.defaultDurationWeeks(),
        template.level().name(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference().name(),
        template.interviewOriented(),
        json(template.topicPreferences()),
        template.targetAudience(),
        json(template.difficultyMix()),
        json(template.prerequisites()),
        json(template.recommendedFor()),
        json(template.notRecommendedFor()),
        template.expectedOutcome(),
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
        phase.durationWeeks(),
        phase.focus(),
        json(phase.objectives()),
        json(phase.recommendedTags()),
        json(phase.acceptanceCriteria()),
        phase.reviewAdvice());
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

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
