package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.PromptAssembly;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptBudgetPolicy;
import org.congcong.algomentor.agent.core.prompt.PromptCachePolicy;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptRenderMode;
import org.congcong.algomentor.agent.core.prompt.PromptSection;
import org.congcong.algomentor.agent.core.prompt.PromptSectionProvider;
import org.congcong.algomentor.agent.core.prompt.PromptSensitivity;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;
import org.congcong.algomentor.agent.core.prompt.PromptSourceRef;
import org.congcong.algomentor.agent.core.prompt.PromptTrustLevel;
import org.congcong.algomentor.agent.core.prompt.RenderedPromptSection;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptResolutionSource;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 将 run-local memory snapshot 渲染为完整项裁剪的 Practice Chat bootstrap section。 */
public final class LearnerMemoryRecallPromptSectionProvider implements PromptSectionProvider {

  private static final String TEXT = "text";

  private final LearnerMemoryRecallBootstrapBuilder bootstrapBuilder;
  private final ManagedSystemPromptResolver systemPromptResolver;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryRecallPromptSectionProvider(
      LearnerMemoryRecallBootstrapBuilder bootstrapBuilder,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    this(bootstrapBuilder, systemPromptResolver, LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryRecallPromptSectionProvider(
      LearnerMemoryRecallBootstrapBuilder bootstrapBuilder,
      ManagedSystemPromptResolver systemPromptResolver,
      LearnerMemoryMetrics metrics
  ) {
    if (bootstrapBuilder == null) {
      throw new IllegalArgumentException("Learner memory bootstrap builder must not be null");
    }
    this.bootstrapBuilder = bootstrapBuilder;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  @Override
  public List<PromptSection> sections(PromptAssemblyRequest request, PromptProfile profile) {
    LearnerMemoryRecallSnapshot snapshot = snapshot(request);
    LearnerMemoryRecallBootstrapBuilder.Bootstrap bootstrap = bootstrap(snapshot, request);
    if (bootstrap.text().isBlank()) {
      return List.of();
    }
    return List.of(new PromptSection(
        LearnerMemoryRecallContracts.SECTION_ID,
        "学习者长期记忆参考",
        PromptSlot.MEMORY_SUMMARY,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.MODEL_GENERATED,
        PromptSensitivity.USER_CONTENT,
        45,
        false,
        "v2",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.DROP_IF_NEEDED,
        PromptRenderMode.BOUNDED_BLOCK,
        new PromptSourceRef(
            "learner-memory",
            "practice-chat",
            sourceAttributes(snapshot, bootstrap)),
        Map.of(TEXT, bootstrap.text())));
  }

  /** 供 conversation 装配器记录不包含 claim 正文的诊断 metadata。 */
  public Map<String, Object> metadata(
      LearnerMemoryRecallSnapshot snapshot,
      PromptAssembly assembly,
      ResolvedSystemPromptSnapshot systemPromptSnapshot
  ) {
    LearnerMemoryRecallBootstrapBuilder.Bootstrap bootstrap = bootstrap(snapshot, systemPromptSnapshot);
    RenderedPromptSection rendered = assembly.renderedSections().stream()
        .filter(section -> LearnerMemoryRecallContracts.SECTION_ID.equals(section.section().id()))
        .findFirst()
        .orElse(null);
    int tokenEstimate = rendered != null && rendered.included() ? rendered.tokenEstimate() : 0;
    boolean trimmed = bootstrap.trimmed() || (rendered != null && rendered.budgetDecision().truncated());
    if (snapshot != null) {
      metrics.recordBootstrap("PRACTICE_CHAT", tokenEstimate, bootstrap.directHitCount(), trimmed);
    }
    return metadata(snapshot, tokenEstimate, trimmed);
  }

  private LearnerMemoryRecallSnapshot snapshot(PromptAssemblyRequest request) {
    Object value = request.variables().get(LearnerMemoryRecallContracts.VARIABLE_SNAPSHOT);
    if (!(value instanceof LearnerMemoryRecallSnapshot snapshot)) {
      return null;
    }
    return snapshot;
  }

  private LearnerMemoryRecallBootstrapBuilder.Bootstrap bootstrap(
      LearnerMemoryRecallSnapshot snapshot,
      PromptAssemblyRequest request
  ) {
    return bootstrap(snapshot, systemPromptSnapshot(request));
  }

  private LearnerMemoryRecallBootstrapBuilder.Bootstrap bootstrap(
      LearnerMemoryRecallSnapshot snapshot,
      ResolvedSystemPromptSnapshot systemPromptSnapshot
  ) {
    return bootstrapBuilder.build(snapshot,
        systemPromptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_LEARNER_PROFILE_BOUNDARY).text());
  }

  private ResolvedSystemPromptSnapshot systemPromptSnapshot(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_SYSTEM_PROMPT_SNAPSHOT);
    if (value instanceof ResolvedSystemPromptSnapshot snapshot
        && ManagedSystemPromptDefinitions.PRACTICE_CHAT.typeCode().equals(snapshot.typeCode())) {
      return snapshot;
    }
    return ManagedSystemPrompts.defaultRegistry().codeDefaultSnapshot(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT, SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);
  }

  private Map<String, Object> sourceAttributes(
      LearnerMemoryRecallSnapshot snapshot,
      LearnerMemoryRecallBootstrapBuilder.Bootstrap bootstrap
  ) {
    return metadata(snapshot, bootstrap.tokenEstimate(), bootstrap.trimmed());
  }

  private Map<String, Object> metadata(
      LearnerMemoryRecallSnapshot snapshot,
      int tokenEstimate,
      boolean trimmed
  ) {
    if (snapshot == null) {
      return Map.of(
          LearnerMemoryRecallContracts.METADATA_SECTION_COUNT, 0,
          LearnerMemoryRecallContracts.METADATA_CLAIM_COUNT, 0,
          LearnerMemoryRecallContracts.METADATA_BOOTSTRAP_TOKEN_ESTIMATE, 0,
          LearnerMemoryRecallContracts.METADATA_BOOTSTRAP_TRIMMED, false);
    }
    return Map.of(
        LearnerMemoryRecallContracts.METADATA_SCOPE_REF, snapshot.scopeRef(),
        LearnerMemoryRecallContracts.METADATA_DOCUMENT_REVISION, snapshot.documentRevision(),
        LearnerMemoryRecallContracts.METADATA_SECTION_COUNT, snapshot.sections().size(),
        LearnerMemoryRecallContracts.METADATA_CLAIM_COUNT, snapshot.claimCount(),
        LearnerMemoryRecallContracts.METADATA_BOOTSTRAP_TOKEN_ESTIMATE, tokenEstimate,
        LearnerMemoryRecallContracts.METADATA_BOOTSTRAP_TRIMMED, trimmed);
  }
}
