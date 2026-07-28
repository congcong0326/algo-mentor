package org.congcong.algomentor.api.learningplan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateImportRun;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningPlanTemplateSeedImportService {

  private final ObjectProvider<LearningPlanTemplateRepository> templateRepositoryProvider;
  private final ObjectProvider<ProblemRepository> problemRepositoryProvider;
  private final LearningPlanTemplateSeedReader reader;
  private final ObjectMapper objectMapper;

  public LearningPlanTemplateSeedImportService(
      ObjectProvider<LearningPlanTemplateRepository> templateRepositoryProvider,
      ObjectProvider<ProblemRepository> problemRepositoryProvider,
      LearningPlanTemplateSeedReader reader,
      ObjectMapper objectMapper
  ) {
    this.templateRepositoryProvider = templateRepositoryProvider;
    this.problemRepositoryProvider = problemRepositoryProvider;
    this.reader = reader;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public LearningPlanTemplateSeedImportResult importSeed(Path seedDirectory) throws IOException {
    LearningPlanTemplateSeedReader.LearningPlanTemplateSeedData seed = reader.read(seedDirectory);
    validate(seed);
    LearningPlanTemplateRepository templateRepository = templateRepository();
    ProblemRepository problemRepository = problemRepository();
    Map<String, List<LearningPlanTemplateProblemRefSeedRecord>> refsByTemplate = seed.problemRefs().stream()
        .collect(Collectors.groupingBy(
            LearningPlanTemplateProblemRefSeedRecord::templateId,
            LinkedHashMap::new,
            Collectors.toList()));

    int matched = 0;
    int missing = 0;
    for (LearningPlanTemplateSeedRecord templateRecord : seed.templates()) {
      List<LearningPlanTemplateProblemRef> refs = new ArrayList<>();
      for (LearningPlanTemplateProblemRefSeedRecord refRecord :
          refsByTemplate.getOrDefault(templateRecord.templateId(), List.of())) {
        boolean problemAvailable = problemRepository.findProblemBySlug(refRecord.problemSlug())
            .map(problem -> hasText(problem.recommendationReason()))
            .orElse(false);
        if (problemAvailable) {
          matched += 1;
        } else {
          missing += 1;
        }
        refs.add(toProblemRef(refRecord, problemAvailable));
      }
      templateRepository.saveTemplate(toTemplate(templateRecord, refs));
    }

    Path manifestPath = seedDirectory.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE);
    Path metadataPath = seedDirectory.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE);
    JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
    String sourceCommit = sourceCommit(manifest, seed.templates());
    templateRepository.insertImportRun(new LearningPlanTemplateImportRun(
        LearningPlanTemplateSeedConstants.SOURCE_NAME,
        sourceCommit,
        seedDirectory.toString(),
        manifestPath.toString(),
        metadataPath.toString(),
        checksum(seedDirectory),
        seed.templates().size(),
        seed.problemRefs().size(),
        matched,
        missing,
        0,
        importMetadata(manifest, metadataPath, seed.templates().size(), seed.problemRefs().size(), matched, missing)));
    return new LearningPlanTemplateSeedImportResult(seed.templates().size(), seed.problemRefs().size(), matched, missing);
  }

  private LearningPlanTemplate toTemplate(
      LearningPlanTemplateSeedRecord record,
      List<LearningPlanTemplateProblemRef> refs
  ) {
    Map<Integer, List<LearningPlanTemplateProblemRef>> refsByPhase = refs.stream()
        .collect(Collectors.groupingBy(
            LearningPlanTemplateProblemRef::phaseIndex,
            LinkedHashMap::new,
            Collectors.toList()));
    List<LearningPlanTemplatePhase> phases = record.phases().stream()
        .map(phase -> new LearningPlanTemplatePhase(
            null,
            phase.phaseIndex(),
            phase.title(),
            phase.durationWeeks(),
            phase.focus(),
            phase.objectives(),
            phase.recommendedTags(),
            phase.acceptanceCriteria(),
            phase.reviewAdvice(),
            refsByPhase.getOrDefault(phase.phaseIndex(), List.of())))
        .toList();
    return new LearningPlanTemplate(
        null,
        record.templateId(),
        record.title(),
        record.summary(),
        record.catalogCategory(),
        record.recommendedOrder(),
        record.intent(),
        record.goal(),
        record.defaultDurationWeeks(),
        record.level(),
        record.defaultWeeklyHours(),
        record.programmingLanguage(),
        record.difficultyPreference(),
        record.interviewOriented(),
        record.topicPreferences(),
        record.targetAudience(),
        record.difficultyMix(),
        record.prerequisites(),
        record.recommendedFor(),
        record.notRecommendedFor(),
        record.expectedOutcome(),
        record.sourceName(),
        record.sourceUrl(),
        record.sourceCommit(),
        record.sourceDataPath(),
        record.sourceDescription(),
        record.curationNotes(),
        record.licenseNotice(),
        refs.size(),
        (int) refs.stream().filter(LearningPlanTemplateProblemRef::matchedProblem).count(),
        (int) refs.stream().filter(ref -> !ref.matchedProblem()).count(),
        record.metadata(),
        phases);
  }

  private LearningPlanTemplateProblemRef toProblemRef(
      LearningPlanTemplateProblemRefSeedRecord record,
      boolean matchedProblem
  ) {
    return new LearningPlanTemplateProblemRef(
        null,
        record.phaseIndex(),
        record.sortOrder(),
        record.sourceOrder(),
        record.problemSlug(),
        record.sourceTitle(),
        record.sourceDifficulty(),
        record.pattern(),
        record.sourceUrl(),
        matchedProblem,
        record.metadata());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private void validate(LearningPlanTemplateSeedReader.LearningPlanTemplateSeedData seed) {
    if (seed.templates().isEmpty()) {
      throw new IllegalArgumentException("Learning plan template seed must contain at least one template.");
    }
    Map<String, LearningPlanTemplateSeedRecord> templatesById = seed.templates().stream()
        .collect(Collectors.toMap(
            LearningPlanTemplateSeedRecord::templateId,
            Function.identity(),
            (left, right) -> {
              throw new IllegalArgumentException("Duplicate learning plan template id: " + left.templateId());
            },
            LinkedHashMap::new));
    Set<Integer> recommendedOrders = new HashSet<>();
    for (LearningPlanTemplateSeedRecord template : seed.templates()) {
      validateTemplate(template);
      if (template.recommendedOrder() != null && !recommendedOrders.add(template.recommendedOrder())) {
        throw new IllegalArgumentException("Duplicate learning plan template recommendedOrder: "
            + template.recommendedOrder());
      }
    }
    if (recommendedOrders.isEmpty()) {
      throw new IllegalArgumentException("Learning plan template seed must include a recommended template.");
    }
    for (int order = 1; order <= recommendedOrders.size(); order++) {
      if (!recommendedOrders.contains(order)) {
        throw new IllegalArgumentException("Learning plan template recommendedOrder must be contiguous from 1.");
      }
    }
    Map<String, Integer> refCountByTemplate = new HashMap<>();
    Map<String, Set<Integer>> phaseIndexesByTemplate = phaseIndexesByTemplate(seed.templates());
    Map<String, Set<Integer>> sourceOrdersByTemplate = new HashMap<>();
    Map<String, Set<String>> slugsByTemplate = new HashMap<>();
    Map<TemplatePhaseKey, List<Integer>> sortOrdersByTemplatePhase = new HashMap<>();
    for (LearningPlanTemplateProblemRefSeedRecord ref : seed.problemRefs()) {
      requireNonBlank(ref.templateId(), "problemRef.templateId");
      if (!templatesById.containsKey(ref.templateId())) {
        throw new IllegalArgumentException("Problem ref points to unknown template: " + ref.templateId());
      }
      if (!phaseIndexesByTemplate.get(ref.templateId()).contains(ref.phaseIndex())) {
        throw new IllegalArgumentException("Problem ref points to unknown template phase: "
            + ref.templateId() + "#" + ref.phaseIndex());
      }
      if (ref.sortOrder() < 1 || ref.sourceOrder() < 1) {
        throw new IllegalArgumentException("Problem ref sortOrder and sourceOrder must be positive: "
            + ref.templateId());
      }
      if (!sourceOrdersByTemplate.computeIfAbsent(ref.templateId(), ignored -> new HashSet<>()).add(ref.sourceOrder())) {
        throw new IllegalArgumentException("Duplicate problem ref sourceOrder in template: "
            + ref.templateId() + "#" + ref.sourceOrder());
      }
      requireNonBlank(ref.problemSlug(), "problemRef.problemSlug");
      requireNonBlank(ref.sourceTitle(), "problemRef.sourceTitle");
      requireNonBlank(ref.pattern(), "problemRef.pattern");
      requireNonBlank(ref.sourceUrl(), "problemRef.sourceUrl");
      if (!slugsByTemplate.computeIfAbsent(ref.templateId(), ignored -> new HashSet<>()).add(ref.problemSlug())
          && !hasRepeatReason(ref)) {
        throw new IllegalArgumentException("Duplicate problem ref slug in template without repeat reason: "
            + ref.templateId() + "#" + ref.problemSlug());
      }
      sortOrdersByTemplatePhase
          .computeIfAbsent(new TemplatePhaseKey(ref.templateId(), ref.phaseIndex()), ignored -> new ArrayList<>())
          .add(ref.sortOrder());
      refCountByTemplate.merge(ref.templateId(), 1, Integer::sum);
    }
    for (String templateId : templatesById.keySet()) {
      if (refCountByTemplate.getOrDefault(templateId, 0) == 0) {
        throw new IllegalArgumentException("Learning plan template has no problem refs: " + templateId);
      }
    }
    validateSortOrders(sortOrdersByTemplatePhase);
  }

  private void validateTemplate(LearningPlanTemplateSeedRecord template) {
    requireNonBlank(template.templateId(), "templateId");
    requireNonBlank(template.title(), "title");
    requireNonBlank(template.summary(), "summary");
    requireNonBlank(template.goal(), "goal");
    requireNonBlank(template.targetAudience(), "targetAudience");
    requireNonEmpty(template.difficultyMix(), "difficultyMix");
    requireNonEmpty(template.prerequisites(), "prerequisites");
    requireNonEmpty(template.recommendedFor(), "recommendedFor");
    requireNonEmpty(template.notRecommendedFor(), "notRecommendedFor");
    requireNonBlank(template.expectedOutcome(), "expectedOutcome");
    requireNonBlank(template.sourceName(), "sourceName");
    requireNonBlank(template.sourceUrl(), "sourceUrl");
    requireNonBlank(template.sourceCommit(), "sourceCommit");
    requireNonBlank(template.sourceDataPath(), "sourceDataPath");
    requireNonBlank(template.sourceDescription(), "sourceDescription");
    requireNonBlank(template.curationNotes(), "curationNotes");
    requireNonBlank(template.licenseNotice(), "licenseNotice");
    if (template.catalogCategory() == null
        || template.intent() == null
        || template.level() == null
        || template.difficultyPreference() == null) {
      throw new IllegalArgumentException("Learning plan template has invalid enum fields: " + template.templateId());
    }
    if (template.recommendedOrder() != null && template.recommendedOrder() < 1) {
      throw new IllegalArgumentException("Learning plan template recommendedOrder must be positive: "
          + template.templateId());
    }
    if (template.defaultDurationWeeks() < 1 || template.defaultWeeklyHours() < 1) {
      throw new IllegalArgumentException("Learning plan template has invalid default duration or hours: "
          + template.templateId());
    }
    if (template.phases().isEmpty()) {
      throw new IllegalArgumentException("Learning plan template has no phases: " + template.templateId());
    }
    int durationWeeks = 0;
    for (int index = 0; index < template.phases().size(); index++) {
      LearningPlanTemplatePhaseSeedRecord phase = template.phases().get(index);
      if (phase.phaseIndex() != index + 1) {
        throw new IllegalArgumentException("Learning plan template phase indexes must be contiguous: "
            + template.templateId());
      }
      if (phase.durationWeeks() < 1) {
        throw new IllegalArgumentException("Learning plan template phase duration must be positive: "
            + template.templateId());
      }
      durationWeeks += phase.durationWeeks();
      requireNonBlank(phase.title(), "phase.title");
      requireNonBlank(phase.focus(), "phase.focus");
      requireNonEmpty(phase.objectives(), "phase.objectives");
      requireNonEmpty(phase.recommendedTags(), "phase.recommendedTags");
      requireNonEmpty(phase.acceptanceCriteria(), "phase.acceptanceCriteria");
      requireNonBlank(phase.reviewAdvice(), "phase.reviewAdvice");
    }
    if (durationWeeks != template.defaultDurationWeeks()) {
      throw new IllegalArgumentException("Learning plan template phase duration sum does not match default duration: "
          + template.templateId());
    }
  }

  private Map<String, Set<Integer>> phaseIndexesByTemplate(List<LearningPlanTemplateSeedRecord> templates) {
    Map<String, Set<Integer>> phaseIndexesByTemplate = new HashMap<>();
    for (LearningPlanTemplateSeedRecord template : templates) {
      Set<Integer> phaseIndexes = new HashSet<>();
      for (LearningPlanTemplatePhaseSeedRecord phase : template.phases()) {
        phaseIndexes.add(phase.phaseIndex());
      }
      phaseIndexesByTemplate.put(template.templateId(), phaseIndexes);
    }
    return phaseIndexesByTemplate;
  }

  private void validateSortOrders(Map<TemplatePhaseKey, List<Integer>> sortOrdersByTemplatePhase) {
    for (Map.Entry<TemplatePhaseKey, List<Integer>> entry : sortOrdersByTemplatePhase.entrySet()) {
      List<Integer> sortOrders = entry.getValue().stream().sorted().toList();
      for (int index = 0; index < sortOrders.size(); index++) {
        if (sortOrders.get(index) != index + 1) {
          throw new IllegalArgumentException("Problem ref sortOrder must be contiguous in template phase: "
              + entry.getKey().templateId() + "#" + entry.getKey().phaseIndex());
        }
      }
    }
  }

  private boolean hasRepeatReason(LearningPlanTemplateProblemRefSeedRecord ref) {
    Object repeatReason = ref.metadata().get(LearningPlanTemplateSeedConstants.REPEAT_REASON_METADATA_KEY);
    return repeatReason instanceof String text && !text.isBlank();
  }

  private Map<String, Object> importMetadata(
      JsonNode manifest,
      Path metadataPath,
      int templateCount,
      int problemRefCount,
      int matched,
      int missing
  ) throws IOException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("manifest", objectMapper.convertValue(manifest, Map.class));
    metadata.put("metadataFilePath", metadataPath.toString());
    metadata.put("metadataFileBytes", Files.size(metadataPath));
    metadata.put("templateCount", templateCount);
    metadata.put("problemRefCount", problemRefCount);
    metadata.put("matchedProblemCount", matched);
    metadata.put("missingProblemCount", missing);
    return metadata;
  }

  private String sourceCommit(JsonNode manifest, List<LearningPlanTemplateSeedRecord> templates) {
    JsonNode manifestCommit = manifest.path("source").path("commit");
    if (manifestCommit.isTextual() && !manifestCommit.asText().isBlank()) {
      return manifestCommit.asText();
    }
    return templates.stream()
        .map(LearningPlanTemplateSeedRecord::sourceCommit)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null);
  }

  private String checksum(Path seedDirectory) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateDigest(digest, seedDirectory.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE));
      updateDigest(digest, seedDirectory.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE));
      updateDigest(digest, seedDirectory.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE));
      updateDigest(digest, seedDirectory.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private void updateDigest(MessageDigest digest, Path path) throws IOException {
    digest.update(path.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    digest.update(Files.readAllBytes(path));
  }

  private void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Learning plan template seed field is required: " + fieldName);
    }
  }

  private void requireNonEmpty(List<?> values, String fieldName) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Learning plan template seed field is required: " + fieldName);
    }
  }

  private void requireNonEmpty(Map<?, ?> values, String fieldName) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Learning plan template seed field is required: " + fieldName);
    }
  }

  private record TemplatePhaseKey(String templateId, int phaseIndex) {
  }

  private LearningPlanTemplateRepository templateRepository() {
    LearningPlanTemplateRepository repository = templateRepositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划模板仓储不可用。");
    }
    return repository;
  }

  private ProblemRepository problemRepository() {
    ProblemRepository repository = problemRepositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return repository;
  }
}
