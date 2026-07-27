package org.congcong.algomentor.mentor.application.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 应用启动时构建的只读系统提示词定义目录及其 schema 校验器。 */
public final class ManagedSystemPromptDefinitionRegistry {

  private static final Pattern TYPE_CODE_PATTERN = Pattern.compile("^ai\\.system-prompt\\.[a-z0-9.-]+\\.v[1-9][0-9]*$");
  private static final Pattern SECTION_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,127}$");
  private final Map<String, ManagedSystemPromptDefinition> definitionsByTypeCode;

  public ManagedSystemPromptDefinitionRegistry(Collection<? extends ManagedSystemPromptDefinition> definitions) {
    Map<String, ManagedSystemPromptDefinition> values = new LinkedHashMap<>();
    if (definitions != null) {
      for (ManagedSystemPromptDefinition definition : definitions) {
        ManagedSystemPromptDefinition nonNullDefinition = Objects.requireNonNull(
            definition, "managedSystemPromptDefinition must not be null");
        validateDefinition(nonNullDefinition);
        ManagedSystemPromptDefinition previous = values.putIfAbsent(nonNullDefinition.typeCode(), nonNullDefinition);
        if (previous != null) {
          throw new IllegalStateException("Duplicate managed system prompt typeCode=" + nonNullDefinition.typeCode());
        }
      }
    }
    // Map.copyOf does not preserve the registry insertion order required by the management catalog.
    this.definitionsByTypeCode = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public ManagedSystemPromptDefinition require(String typeCode) {
    String normalized = typeCode == null ? "" : typeCode.trim().toLowerCase(Locale.ROOT);
    ManagedSystemPromptDefinition definition = definitionsByTypeCode.get(normalized);
    if (definition == null) {
      throw new IllegalArgumentException("Managed system prompt type is not registered: " + normalized);
    }
    return definition;
  }

  public ManagedSystemPromptDefinition requireRegisteredInstance(ManagedSystemPromptDefinition definition) {
    Objects.requireNonNull(definition, "definition must not be null");
    ManagedSystemPromptDefinition registered = require(definition.typeCode());
    if (registered != definition) {
      throw new IllegalArgumentException("System prompt resolution must use a registered definition instance: "
          + definition.typeCode());
    }
    return definition;
  }

  public List<ManagedSystemPromptDefinition> definitions() {
    return definitionsByTypeCode.values().stream()
        .sorted(Comparator.comparing(ManagedSystemPromptDefinition::typeCode))
        .toList();
  }

  public void validatePolicyContent(ManagedSystemPromptDefinition definition, ManagedSystemPromptPolicyContent content) {
    ManagedSystemPromptDefinition registered = requireRegisteredInstance(definition);
    Map<String, ManagedSystemPromptSectionDefinition> sections = sectionsByKey(registered);
    if (content == null) {
      throw new IllegalArgumentException("sectionOverrides must not be null");
    }
    content.sectionOverrides().forEach((key, text) -> {
      ManagedSystemPromptSectionDefinition section = sections.get(key);
      if (section == null) {
        throw new IllegalArgumentException("Unknown system prompt section: " + key);
      }
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("System prompt override must not be blank: " + key);
      }
      if (text.length() > section.maxLength()) {
        throw new IllegalArgumentException("System prompt override exceeds max length: " + key);
      }
    });
  }

  public ResolvedSystemPromptSnapshot codeDefaultSnapshot(
      ManagedSystemPromptDefinition definition,
      SystemPromptResolutionSource source
  ) {
    return merge(definition, new ManagedSystemPromptPolicyContent(Map.of()), source, null, null, null, null);
  }

  public ResolvedSystemPromptSnapshot merge(
      ManagedSystemPromptDefinition definition,
      ManagedSystemPromptPolicyContent content,
      SystemPromptResolutionSource source,
      Long policyId,
      Long policyVersion,
      SystemPromptMatchSource matchSource,
      Long matchedSubjectId
  ) {
    ManagedSystemPromptDefinition registered = requireRegisteredInstance(definition);
    validatePolicyContent(registered, content);
    Map<String, ResolvedSystemPromptSection> sections = new LinkedHashMap<>();
    for (ManagedSystemPromptSectionDefinition section : registered.sections()) {
      String override = content.sectionOverrides().get(section.key());
      String text = override == null ? section.defaultText() : override;
      ResolvedSystemPromptSectionSource sectionSource = override == null
          ? ResolvedSystemPromptSectionSource.CODE_DEFAULT
          : ResolvedSystemPromptSectionSource.POLICY_OVERRIDE;
      sections.put(section.key(), new ResolvedSystemPromptSection(
          section.key(), text, sectionSource, sha256(text), text.length()));
    }
    String combined = registered.typeCode() + "\\n" + registered.sourceRevision() + "\\n"
        + sections.values().stream().map(ResolvedSystemPromptSection::contentHash).collect(java.util.stream.Collectors.joining("\\n"));
    return new ResolvedSystemPromptSnapshot(
        registered.typeCode(),
        registered.sourceRevision(),
        source,
        policyId,
        policyVersion,
        matchSource,
        matchedSubjectId,
        sections,
        sha256(combined));
  }

  public static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(Objects.requireNonNull(value, "value must not be null").getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }

  private void validateDefinition(ManagedSystemPromptDefinition definition) {
    String typeCode = definition.typeCode();
    if (typeCode == null || !TYPE_CODE_PATTERN.matcher(typeCode).matches()) {
      throw new IllegalArgumentException("Invalid managed system prompt typeCode: " + typeCode);
    }
    if (definition.sourceRevision() == null || definition.sourceRevision().isBlank()) {
      throw new IllegalArgumentException("System prompt sourceRevision must not be blank: " + typeCode);
    }
    if (definition.snapshotScope() == null || definition.descriptor() == null) {
      throw new IllegalArgumentException("System prompt scope and descriptor are required: " + typeCode);
    }
    ManagedSystemPromptTypeDescriptor descriptor = definition.descriptor();
    if (isBlank(descriptor.categoryCode()) || isBlank(descriptor.displayNameZh()) || isBlank(descriptor.descriptionZh())) {
      throw new IllegalArgumentException("System prompt descriptor is incomplete: " + typeCode);
    }
    List<ManagedSystemPromptSectionDefinition> sections = definition.sections();
    if (sections == null || sections.isEmpty()) {
      throw new IllegalArgumentException("System prompt definition must have sections: " + typeCode);
    }
    Map<String, ManagedSystemPromptSectionDefinition> byKey = sectionsByKey(definition);
    if (byKey.size() != sections.size()) {
      throw new IllegalArgumentException("Duplicate system prompt section key: " + typeCode);
    }
    List<Integer> orders = new ArrayList<>();
    for (ManagedSystemPromptSectionDefinition section : sections) {
      if (section == null || section.key() == null || !SECTION_KEY_PATTERN.matcher(section.key()).matches()) {
        throw new IllegalArgumentException("Invalid system prompt section key: " + typeCode);
      }
      if (isBlank(section.displayNameZh()) || isBlank(section.descriptionZh()) || section.displayOrder() < 0
          || section.maxLength() < 1 || section.defaultText() == null || section.defaultText().length() > section.maxLength()
          || (section.required() && section.defaultText().isBlank())) {
        throw new IllegalArgumentException("Invalid system prompt section definition: " + section.key());
      }
      orders.add(section.displayOrder());
    }
    if (orders.stream().distinct().count() != orders.size()) {
      throw new IllegalArgumentException("Duplicate system prompt section displayOrder: " + typeCode);
    }
  }

  private Map<String, ManagedSystemPromptSectionDefinition> sectionsByKey(ManagedSystemPromptDefinition definition) {
    Map<String, ManagedSystemPromptSectionDefinition> sections = new LinkedHashMap<>();
    for (ManagedSystemPromptSectionDefinition section : definition.sections()) {
      if (section != null) {
        sections.putIfAbsent(section.key(), section);
      }
    }
    return sections;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
