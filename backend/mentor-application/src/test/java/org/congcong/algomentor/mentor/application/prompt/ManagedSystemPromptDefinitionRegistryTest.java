package org.congcong.algomentor.mentor.application.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
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
            SystemPromptTypeCodes.PRACTICE_CODE_REVIEW_V1,
            SystemPromptTypeCodes.TOPIC_EXPLANATION_V1));
    assertThat(registry.definitions()).extracting(ManagedSystemPromptDefinition::scenario)
        .containsExactlyInAnyOrderElementsOf(List.of(AiBusinessScenario.values()));
  }

  @Test
  void codeReviewProfilePromptRequiresRecognizableCrossProblemObservations() {
    ManagedSystemPromptDefinition definition = ManagedSystemPromptDefinitions.CODE_REVIEW_PROFILE_UPDATE;
    String prompt = definition.sections().stream()
        .filter(section -> SystemPromptSectionKeys.CODE_REVIEW_PROFILE_UPDATE_BASE.equals(section.key()))
        .findFirst()
        .orElseThrow()
        .defaultText();

    assertThat(definition.sourceRevision()).isEqualTo("2026-07-29.1");
    assertThat(prompt)
        .contains("保留能够唤起学习经历的具体锚点")
        .contains("至少两道不同题目")
        .contains("证据不足时返回 NO_CHANGE")
        .contains("PROBLEM_SOLVING_APPROACH")
        .contains("IMPLEMENTATION_AND_ERROR_PATTERN")
        .contains("2 至 4 句话");
  }
}
