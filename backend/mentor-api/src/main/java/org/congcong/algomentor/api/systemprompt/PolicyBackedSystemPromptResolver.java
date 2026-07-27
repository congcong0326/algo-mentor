package org.congcong.algomentor.api.systemprompt;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitionRegistry;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMatchSource;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptResolutionSource;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在策略查询边界捕获失败并明确回退到代码默认提示词。 */
public final class PolicyBackedSystemPromptResolver implements ManagedSystemPromptResolver {

  private static final Logger log = LoggerFactory.getLogger(PolicyBackedSystemPromptResolver.class);
  private final ManagedSystemPromptDefinitionRegistry definitionRegistry;
  private final PromptPolicyTypeContributor typeContributor;
  private final GenericPolicyQueryService policyQueryService;
  private final boolean enabled;

  public PolicyBackedSystemPromptResolver(
      ManagedSystemPromptDefinitionRegistry definitionRegistry,
      PromptPolicyTypeContributor typeContributor,
      GenericPolicyQueryService policyQueryService,
      boolean enabled
  ) {
    this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "definitionRegistry must not be null");
    this.typeContributor = Objects.requireNonNull(typeContributor, "typeContributor must not be null");
    this.policyQueryService = Objects.requireNonNull(policyQueryService, "policyQueryService must not be null");
    this.enabled = enabled;
  }

  @Override
  public ResolvedSystemPromptSnapshot resolve(ManagedSystemPromptDefinition definition, long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    definitionRegistry.requireRegisteredInstance(definition);
    if (!enabled) {
      return definitionRegistry.codeDefaultSnapshot(definition, SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);
    }
    try {
      return policyQueryService.resolve(typeContributor.require(definition), userId)
          .map(policy -> merge(definition, policy))
          .orElseGet(() -> definitionRegistry.codeDefaultSnapshot(
              definition, SystemPromptResolutionSource.CODE_NO_MATCH));
    } catch (IllegalArgumentException exception) {
      log.error("System prompt policy is invalid; falling back to code default. typeCode={} exceptionType={}",
          definition.typeCode(), exception.getClass().getSimpleName());
      return definitionRegistry.codeDefaultSnapshot(definition, SystemPromptResolutionSource.CODE_INVALID_POLICY);
    } catch (RuntimeException exception) {
      log.error("System prompt policy resolution failed; falling back to code default. typeCode={} exceptionType={}",
          definition.typeCode(), exception.getClass().getSimpleName());
      return definitionRegistry.codeDefaultSnapshot(definition, SystemPromptResolutionSource.CODE_RESOLUTION_FAILURE);
    }
  }

  private ResolvedSystemPromptSnapshot merge(
      ManagedSystemPromptDefinition definition,
      org.congcong.algomentor.policy.model.ResolvedPolicy<
          org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptPolicyContent> policy
  ) {
    if (!definition.typeCode().equals(policy.typeCode())) {
      throw new IllegalArgumentException("Resolved policy typeCode does not match system prompt definition");
    }
    return definitionRegistry.merge(
        definition,
        policy.content(),
        SystemPromptResolutionSource.POLICY,
        policy.policyId(),
        policy.version(),
        matchSource(policy.matchSource()),
        policy.matchedSubjectId());
  }

  private static SystemPromptMatchSource matchSource(PolicyMatchSource source) {
    return switch (source) {
      case USER -> SystemPromptMatchSource.USER;
      case GROUP -> SystemPromptMatchSource.GROUP;
      case ALL -> SystemPromptMatchSource.ALL;
    };
  }
}
