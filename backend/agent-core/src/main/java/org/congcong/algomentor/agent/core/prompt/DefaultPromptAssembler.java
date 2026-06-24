package org.congcong.algomentor.agent.core.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public class DefaultPromptAssembler implements PromptAssembler {

  private static final int MAX_PUBLIC_METADATA_STRING_LENGTH = 160;

  private final PromptProfileResolver profileResolver;
  private final List<PromptSectionProvider> sectionProviders;
  private final PromptRenderer renderer;
  private final PromptBudgetPlanner budgetPlanner;
  private final PromptMessageNormalizer messageNormalizer;

  public DefaultPromptAssembler(
      PromptProfileResolver profileResolver,
      List<PromptSectionProvider> sectionProviders
  ) {
    this(
        profileResolver,
        sectionProviders,
        new DefaultPromptRenderer(),
        new DefaultPromptBudgetPlanner(),
        new DefaultPromptMessageNormalizer());
  }

  public DefaultPromptAssembler(
      PromptProfileResolver profileResolver,
      List<PromptSectionProvider> sectionProviders,
      PromptRenderer renderer,
      PromptBudgetPlanner budgetPlanner,
      PromptMessageNormalizer messageNormalizer
  ) {
    if (profileResolver == null) {
      throw new IllegalArgumentException("Prompt profile resolver must not be null");
    }
    if (sectionProviders == null || sectionProviders.isEmpty()) {
      throw new IllegalArgumentException("Prompt section providers must not be empty");
    }
    this.profileResolver = profileResolver;
    this.sectionProviders = List.copyOf(sectionProviders);
    this.renderer = renderer == null ? new DefaultPromptRenderer() : renderer;
    this.budgetPlanner = budgetPlanner == null ? new DefaultPromptBudgetPlanner() : budgetPlanner;
    this.messageNormalizer = messageNormalizer == null ? new DefaultPromptMessageNormalizer() : messageNormalizer;
  }

  @Override
  public PromptAssembly assemble(PromptAssemblyRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Prompt assembly request must not be null");
    }
    PromptProfile profile = profileResolver.resolve(request);
    if (profile == null) {
      throw new PromptAssemblyException("Prompt profile resolver returned null");
    }

    List<PromptSection> sections = collectSections(request, profile);
    validateRequiredSections(profile, sections);

    List<RenderedPromptSection> initialRenderedSections = sections.stream()
        .map(renderer::render)
        .toList();
    validateRequiredRenderedSections(initialRenderedSections);

    Map<String, PromptBudgetDecision> decisions = decisionsBySectionId(
        budgetPlanner.plan(request, profile, initialRenderedSections));
    List<RenderedPromptSection> renderedSections = sections.stream()
        .map(section -> renderer.render(section, decisions.getOrDefault(section.id(), PromptBudgetDecision.keep(section.id()))))
        .toList();
    failRequiredOverBudget(renderedSections);

    List<LlmMessage> messages = messageNormalizer.normalize(profile, renderedSections);
    if (messages.isEmpty()) {
      throw new PromptAssemblyException("Prompt assembly produced no canonical messages");
    }

    List<PromptSectionSnapshot> snapshots = renderedSections.stream()
        .map(this::snapshot)
        .toList();
    int tokenEstimate = renderedSections.stream()
        .filter(RenderedPromptSection::included)
        .mapToInt(RenderedPromptSection::tokenEstimate)
        .sum();
    return new PromptAssembly(
        profile,
        messages,
        renderedSections,
        snapshots,
        metadata(request, profile, renderedSections, snapshots, tokenEstimate),
        tokenEstimate);
  }

  private List<PromptSection> collectSections(PromptAssemblyRequest request, PromptProfile profile) {
    List<PromptSection> sections = new ArrayList<>();
    for (PromptSectionProvider provider : sectionProviders) {
      List<PromptSection> provided = provider.sections(request, profile);
      if (provided != null) {
        sections.addAll(provided);
      }
    }
    Set<String> duplicateIds = sections.stream()
        .collect(Collectors.groupingBy(PromptSection::id, Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
    if (!duplicateIds.isEmpty()) {
      throw new PromptAssemblyException("Duplicate prompt section ids: " + duplicateIds);
    }
    return List.copyOf(sections);
  }

  private void validateRequiredSections(PromptProfile profile, List<PromptSection> sections) {
    Set<String> sectionIds = sections.stream()
        .map(PromptSection::id)
        .collect(Collectors.toSet());
    List<String> missingRequiredIds = profile.requiredSectionIds()
        .stream()
        .filter(id -> !sectionIds.contains(id))
        .sorted()
        .toList();
    if (!missingRequiredIds.isEmpty()) {
      throw new PromptAssemblyException("Missing required prompt sections: " + missingRequiredIds);
    }
  }

  private void validateRequiredRenderedSections(List<RenderedPromptSection> renderedSections) {
    List<String> blankRequiredIds = renderedSections.stream()
        .filter(rendered -> rendered.section().required())
        .filter(rendered -> rendered.renderedText().isBlank())
        .map(rendered -> rendered.section().id())
        .sorted()
        .toList();
    if (!blankRequiredIds.isEmpty()) {
      throw new PromptAssemblyException("Required prompt sections rendered blank: " + blankRequiredIds);
    }
  }

  private Map<String, PromptBudgetDecision> decisionsBySectionId(List<PromptBudgetDecision> decisions) {
    return decisions.stream()
        .collect(Collectors.toMap(
            PromptBudgetDecision::sectionId,
            Function.identity(),
            (left, right) -> right,
            LinkedHashMap::new));
  }

  private void failRequiredOverBudget(List<RenderedPromptSection> renderedSections) {
    List<String> failedIds = renderedSections.stream()
        .filter(rendered -> rendered.budgetDecision().action() == PromptBudgetAction.FAIL_REQUIRED)
        .map(rendered -> rendered.section().id())
        .sorted()
        .toList();
    if (!failedIds.isEmpty()) {
      throw new PromptAssemblyException("Required prompt sections exceed budget: " + failedIds);
    }
  }

  private PromptSectionSnapshot snapshot(RenderedPromptSection rendered) {
    PromptSection section = rendered.section();
    return new PromptSectionSnapshot(
        section.id(),
        section.title(),
        section.slot(),
        section.targetRole(),
        section.trustLevel(),
        section.sensitivity(),
        section.version(),
        section.sourceRef(),
        rendered.included(),
        rendered.budgetDecision().truncated(),
        rendered.charCount(),
        rendered.tokenEstimate(),
        hash(rendered.renderedText()),
        redactedVariables(section));
  }

  private Map<String, Object> metadata(
      PromptAssemblyRequest request,
      PromptProfile profile,
      List<RenderedPromptSection> renderedSections,
      List<PromptSectionSnapshot> snapshots,
      int tokenEstimate
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(redactedMap(request.metadata(), false));
    metadata.put(AgentPromptMetadataKeys.PROMPT_PROFILE, profile.id());
    metadata.put(AgentPromptMetadataKeys.PROMPT_PROFILE_VERSION, profile.version());
    metadata.put(AgentPromptMetadataKeys.PROMPT_SECTION_VERSIONS, sectionVersions(renderedSections));
    metadata.put(AgentPromptMetadataKeys.PROMPT_POLICY, profile.policyName());
    metadata.put(AgentPromptMetadataKeys.PROMPT_POLICY_VERSION, profile.policyVersion());
    metadata.put(
        AgentPromptMetadataKeys.PROMPT_TOKEN_BUDGET,
        request.tokenBudget() > 0 ? request.tokenBudget() : profile.tokenBudget());
    metadata.put(AgentPromptMetadataKeys.PROMPT_TOKEN_ESTIMATE, tokenEstimate);
    metadata.put(AgentPromptMetadataKeys.PROMPT_TRUNCATED_SECTIONS, changedSectionIds(renderedSections));
    metadata.put(AgentPromptMetadataKeys.PROMPT_CONTENT_HASHES, contentHashes(snapshots));
    return Map.copyOf(metadata);
  }

  private Map<String, String> sectionVersions(List<RenderedPromptSection> renderedSections) {
    return renderedSections.stream()
        .collect(Collectors.toMap(
            rendered -> rendered.section().id(),
            rendered -> rendered.section().version(),
            (left, right) -> right,
            LinkedHashMap::new));
  }

  private List<String> changedSectionIds(List<RenderedPromptSection> renderedSections) {
    return renderedSections.stream()
        .filter(rendered -> rendered.budgetDecision().action() != PromptBudgetAction.KEEP)
        .map(rendered -> rendered.section().id())
        .toList();
  }

  private Map<String, String> contentHashes(List<PromptSectionSnapshot> snapshots) {
    return snapshots.stream()
        .collect(Collectors.toMap(
            PromptSectionSnapshot::id,
            PromptSectionSnapshot::contentHash,
            (left, right) -> right,
            LinkedHashMap::new));
  }

  private Map<String, Object> redactedVariables(PromptSection section) {
    boolean redactAllValues = section.sensitivity() == PromptSensitivity.USER_CONTENT
        || section.sensitivity() == PromptSensitivity.INTERNAL_TRACE;
    return redactedMap(section.variables(), redactAllValues);
  }

  private Map<String, Object> redactedMap(Map<String, Object> values, boolean redactAllValues) {
    Map<String, Object> redacted = new HashMap<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      redacted.put(entry.getKey(), redactedValue(entry.getKey(), entry.getValue(), redactAllValues));
    }
    return Map.copyOf(redacted);
  }

  @SuppressWarnings("unchecked")
  private Object redactedValue(String key, Object value, boolean redactAllValues) {
    if (value == null) {
      return null;
    }
    if (isSensitiveKey(key) || redactAllValues) {
      return redactionSummary(value);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> nested = new HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() instanceof String nestedKey) {
          nested.put(nestedKey, redactedValue(nestedKey, entry.getValue(), false));
        }
      }
      return Map.copyOf(nested);
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(item -> item instanceof Map<?, ?> map
              ? redactedValue(key, (Map<String, Object>) map, false)
              : publicValue(key, item))
          .toList();
    }
    return publicValue(key, value);
  }

  private Object publicValue(String key, Object value) {
    if (value instanceof String text && text.length() > MAX_PUBLIC_METADATA_STRING_LENGTH) {
      return "[REDACTED chars=" + text.length() + "]";
    }
    return value;
  }

  private Object redactionSummary(Object value) {
    if (value instanceof String text) {
      return "[REDACTED chars=" + text.length() + "]";
    }
    if (value instanceof List<?> list) {
      return "[REDACTED items=" + list.size() + "]";
    }
    if (value instanceof Map<?, ?> map) {
      return "[REDACTED fields=" + map.size() + "]";
    }
    return "[REDACTED]";
  }

  private boolean isSensitiveKey(String key) {
    String normalized = key == null ? "" : key.toLowerCase();
    if (normalized.endsWith("intent")) {
      return false;
    }
    return normalized.contains("authorization")
        || normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("apikey")
        || normalized.contains("api_key")
        || normalized.contains("key")
        || normalized.contains("code")
        || normalized.contains("message")
        || normalized.contains("content")
        || normalized.contains("prompt");
  }

  private String hash(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new PromptAssemblyException("SHA-256 is not available");
    }
  }
}
