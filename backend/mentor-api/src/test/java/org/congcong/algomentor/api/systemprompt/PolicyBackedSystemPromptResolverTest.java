package org.congcong.algomentor.api.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitionRegistry;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptPolicyContent;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSectionSource;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptResolutionSource;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.junit.jupiter.api.Test;

class PolicyBackedSystemPromptResolverTest {

  @Test
  void mergesMatchedPolicyOverrideAndKeepsPolicyIdentity() {
    TestFixture fixture = fixture(true);
    ManagedSystemPromptPolicyContent content = new ManagedSystemPromptPolicyContent(Map.of(
        SystemPromptSectionKeys.PRACTICE_INTERACTION, "覆盖后的互动策略。"));
    when(fixture.queryService.resolve(fixture.contributor.require(ManagedSystemPromptDefinitions.PRACTICE_CHAT), 42L))
        .thenReturn(Optional.of(new ResolvedPolicy<>(
            7L,
            ManagedSystemPromptDefinitions.PRACTICE_CHAT.typeCode(),
            "测试用户覆盖",
            1,
            content,
            PolicyMatchSource.USER,
            42L,
            4L)));

    ResolvedSystemPromptSnapshot snapshot = fixture.resolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CHAT, 42L);

    assertThat(snapshot.resolutionSource()).isEqualTo(SystemPromptResolutionSource.POLICY);
    assertThat(snapshot.policyId()).isEqualTo(7L);
    assertThat(snapshot.policyVersion()).isEqualTo(4L);
    assertThat(snapshot.requireSection(SystemPromptSectionKeys.PRACTICE_INTERACTION))
        .extracting(section -> section.text(), section -> section.source())
        .containsExactly("覆盖后的互动策略。", ResolvedSystemPromptSectionSource.POLICY_OVERRIDE);
  }

  @Test
  void fallsBackWhenPolicyIsDisabledOrNoPolicyMatches() {
    TestFixture disabled = fixture(false);
    assertThat(disabled.resolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CHAT, 42L).resolutionSource())
        .isEqualTo(SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);

    TestFixture noMatch = fixture(true);
    when(noMatch.queryService.resolve(noMatch.contributor.require(ManagedSystemPromptDefinitions.PRACTICE_CHAT), 42L))
        .thenReturn(Optional.empty());
    assertThat(noMatch.resolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CHAT, 42L).resolutionSource())
        .isEqualTo(SystemPromptResolutionSource.CODE_NO_MATCH);
  }

  @Test
  void fallsBackWithoutLeakingInvalidOrUnavailablePolicyContent() {
    TestFixture fixture = fixture(true);
    when(fixture.queryService.resolve(fixture.contributor.require(ManagedSystemPromptDefinitions.PRACTICE_CHAT), 42L))
        .thenReturn(Optional.of(new ResolvedPolicy<>(
            7L,
            ManagedSystemPromptDefinitions.PRACTICE_CHAT.typeCode(),
            "损坏策略",
            1,
            new ManagedSystemPromptPolicyContent(Map.of("practice.unknown", "不应使用")),
            PolicyMatchSource.ALL,
            null,
            4L)));

    ResolvedSystemPromptSnapshot invalid = fixture.resolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CHAT, 42L);

    assertThat(invalid.resolutionSource()).isEqualTo(SystemPromptResolutionSource.CODE_INVALID_POLICY);
    assertThat(invalid.policyId()).isNull();
    assertThat(invalid.requireSection(SystemPromptSectionKeys.PRACTICE_INTERACTION).text())
        .isEqualTo(ManagedSystemPromptDefinitions.PRACTICE_CHAT.sections().stream()
            .filter(section -> section.key().equals(SystemPromptSectionKeys.PRACTICE_INTERACTION))
            .findFirst()
            .orElseThrow()
            .defaultText());

    TestFixture unavailable = fixture(true);
    when(unavailable.queryService.resolve(unavailable.contributor.require(ManagedSystemPromptDefinitions.PRACTICE_CHAT), 42L))
        .thenThrow(new IllegalStateException("cache unavailable"));
    assertThat(unavailable.resolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CHAT, 42L).resolutionSource())
        .isEqualTo(SystemPromptResolutionSource.CODE_RESOLUTION_FAILURE);
  }

  private TestFixture fixture(boolean enabled) {
    ManagedSystemPromptDefinitionRegistry registry = new ManagedSystemPromptDefinitionRegistry(
        ManagedSystemPromptDefinitions.all());
    PromptPolicyTypeContributor contributor = new PromptPolicyTypeContributor(registry);
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    return new TestFixture(queryService, contributor,
        new PolicyBackedSystemPromptResolver(registry, contributor, queryService, enabled));
  }

  private record TestFixture(
      GenericPolicyQueryService queryService,
      PromptPolicyTypeContributor contributor,
      PolicyBackedSystemPromptResolver resolver
  ) {
  }
}
