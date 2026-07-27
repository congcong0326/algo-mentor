package org.congcong.algomentor.mentor.application.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

/** 已解析策略的受信 metadata 键，绝不包含系统提示词正文。 */
public final class SystemPromptMetadataKeys {

  public static final String TYPE_CODE = "systemPromptTypeCode";
  public static final String SOURCE_REVISION = "systemPromptSourceRevision";
  public static final String RESOLUTION_SOURCE = "systemPromptResolutionSource";
  public static final String POLICY_ID = "systemPromptPolicyId";
  public static final String POLICY_VERSION = "systemPromptPolicyVersion";
  public static final String MATCH_SOURCE = "systemPromptMatchSource";
  public static final String MATCHED_SUBJECT_ID = "systemPromptMatchedSubjectId";
  public static final String SECTION_SOURCES = "systemPromptSectionSources";
  public static final String CONTENT_HASHES = "systemPromptContentHashes";

  private SystemPromptMetadataKeys() {
  }

  public static Map<String, Object> from(ResolvedSystemPromptSnapshot snapshot) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(TYPE_CODE, snapshot.typeCode());
    metadata.put(SOURCE_REVISION, snapshot.sourceRevision());
    metadata.put(RESOLUTION_SOURCE, snapshot.resolutionSource().name());
    if (snapshot.policyId() != null) {
      metadata.put(POLICY_ID, snapshot.policyId());
      metadata.put(POLICY_VERSION, snapshot.policyVersion());
      metadata.put(MATCH_SOURCE, snapshot.matchSource().name());
      metadata.put(MATCHED_SUBJECT_ID, snapshot.matchedSubjectId());
    }
    Map<String, String> sectionSources = new LinkedHashMap<>();
    Map<String, String> contentHashes = new LinkedHashMap<>();
    snapshot.sections().forEach((key, section) -> {
      sectionSources.put(key, section.source().name());
      contentHashes.put(key, section.contentHash());
    });
    metadata.put(SECTION_SOURCES, java.util.Collections.unmodifiableMap(sectionSources));
    metadata.put(CONTENT_HASHES, java.util.Collections.unmodifiableMap(contentHashes));
    return java.util.Collections.unmodifiableMap(metadata);
  }
}
