package org.congcong.algomentor.mentor.application.prompt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/** 某次业务执行固定使用的系统提示词不可变快照。 */
public record ResolvedSystemPromptSnapshot(
    String typeCode,
    String sourceRevision,
    SystemPromptResolutionSource resolutionSource,
    Long policyId,
    Long policyVersion,
    SystemPromptMatchSource matchSource,
    Long matchedSubjectId,
    Map<String, ResolvedSystemPromptSection> sections,
    String combinedContentHash
) {

  public ResolvedSystemPromptSnapshot {
    typeCode = requireText(typeCode, "typeCode");
    sourceRevision = requireText(sourceRevision, "sourceRevision");
    resolutionSource = Objects.requireNonNull(resolutionSource, "resolutionSource must not be null");
    if (policyId != null && policyId < 1) {
      throw new IllegalArgumentException("policyId must be positive");
    }
    if (policyVersion != null && policyVersion < 1) {
      throw new IllegalArgumentException("policyVersion must be positive");
    }
    if ((policyId == null) != (policyVersion == null)) {
      throw new IllegalArgumentException("policy identity must contain both id and version");
    }
    sections = immutableSections(sections);
    combinedContentHash = requireText(combinedContentHash, "combinedContentHash");
  }

  public ResolvedSystemPromptSection requireSection(String key) {
    ResolvedSystemPromptSection section = sections.get(key);
    if (section == null) {
      throw new IllegalArgumentException("System prompt section is not registered: " + key);
    }
    return section;
  }

  private static Map<String, ResolvedSystemPromptSection> immutableSections(
      Map<String, ResolvedSystemPromptSection> values
  ) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("sections must not be empty");
    }
    Map<String, ResolvedSystemPromptSection> copy = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      if (key == null || key.isBlank() || value == null || !key.equals(value.key())) {
        throw new IllegalArgumentException("sections must be keyed by their section key");
      }
      if (copy.putIfAbsent(key, value) != null) {
        throw new IllegalArgumentException("duplicate section key: " + key);
      }
    });
    return Collections.unmodifiableMap(copy);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
