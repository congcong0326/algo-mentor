package org.congcong.algomentor.mentor.application.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedSystemPromptDefinitionRegistryTest {

  private final ManagedSystemPromptDefinitionRegistry registry =
      new ManagedSystemPromptDefinitionRegistry(ManagedSystemPromptDefinitions.all());

  @Test
  void mergesOnlyRegisteredOverridesAndKeepsSectionOrderImmutable() {
    ResolvedSystemPromptSnapshot snapshot = registry.merge(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT,
        new ManagedSystemPromptPolicyContent(Map.of(
            SystemPromptSectionKeys.PRACTICE_INTERACTION, "覆盖后的训练互动规则。")),
        SystemPromptResolutionSource.POLICY,
        8L,
        3L,
        SystemPromptMatchSource.USER,
        42L);

    assertThat(snapshot.sections().keySet()).containsExactlyElementsOf(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT.sections().stream()
            .map(ManagedSystemPromptSectionDefinition::key)
            .toList());
    assertThat(snapshot.requireSection(SystemPromptSectionKeys.PRACTICE_INTERACTION))
        .extracting(ResolvedSystemPromptSection::text, ResolvedSystemPromptSection::source)
        .containsExactly("覆盖后的训练互动规则。", ResolvedSystemPromptSectionSource.POLICY_OVERRIDE);
    assertThat(snapshot.requireSection(SystemPromptSectionKeys.PRACTICE_BASE_IDENTITY).source())
        .isEqualTo(ResolvedSystemPromptSectionSource.CODE_DEFAULT);
    assertThatThrownBy(() -> snapshot.sections().put("other", snapshot.requireSection(
        SystemPromptSectionKeys.PRACTICE_INTERACTION)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsUnknownAndBlankOverrides() {
    assertThatThrownBy(() -> registry.validatePolicyContent(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT,
        new ManagedSystemPromptPolicyContent(Map.of("practice.unknown", "正文"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown system prompt section");
    assertThatThrownBy(() -> registry.validatePolicyContent(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT,
        new ManagedSystemPromptPolicyContent(Map.of(SystemPromptSectionKeys.PRACTICE_INTERACTION, "  "))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void registersTheInitialMigrationTypeCatalog() {
    assertThat(registry.definitions()).extracting(ManagedSystemPromptDefinition::typeCode)
        .containsExactlyElementsOf(List.of(
            SystemPromptTypeCodes.CODE_REVIEW_PROFILE_UPDATE_V1,
            SystemPromptTypeCodes.LEARNER_DECLARED_PROFILE_UPDATE_V1,
            SystemPromptTypeCodes.LEARNING_PLAN_DRAFT_V1,
            SystemPromptTypeCodes.LEARNING_PLAN_EXTENSION_V1,
            SystemPromptTypeCodes.LEARNING_PLAN_REVISION_V1,
            SystemPromptTypeCodes.MENTOR_CONVERSATION_V1,
            SystemPromptTypeCodes.PRACTICE_CHAT_V1,
            SystemPromptTypeCodes.PRACTICE_CODE_REVIEW_V1));
  }
}
