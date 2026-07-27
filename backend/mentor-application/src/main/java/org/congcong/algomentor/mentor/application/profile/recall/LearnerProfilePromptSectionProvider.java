package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.ArrayList;
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

/** 把固定的单 run 画像快照渲染为受独立 token 预算控制的参考性 memory section。 */
public final class LearnerProfilePromptSectionProvider implements PromptSectionProvider {

  public static final int DEFAULT_MAX_TOKEN_BUDGET = 800;

  private static final String TEXT = "text";
  private static final String TRUNCATED_MARKER = " [已截断]";
  private static final int RENDER_CHROME_CHARS = 96;
  private static final int CHARS_PER_TOKEN = 4;

  private final int maxTokenBudget;
  private final ManagedSystemPromptResolver systemPromptResolver;

  public LearnerProfilePromptSectionProvider(int maxTokenBudget) {
    this(maxTokenBudget, ManagedSystemPrompts.defaultResolver());
  }

  public LearnerProfilePromptSectionProvider(
      int maxTokenBudget,
      ManagedSystemPromptResolver systemPromptResolver
  ) {
    if (maxTokenBudget < 1) {
      throw new IllegalArgumentException("Learner profile prompt token budget must be positive");
    }
    this.maxTokenBudget = maxTokenBudget;
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  @Override
  public List<PromptSection> sections(PromptAssemblyRequest request, PromptProfile profile) {
    LearnerProfileRecallSnapshot snapshot = snapshot(request);
    RenderPlan plan = renderPlan(snapshot, systemPromptSnapshot(request));
    if (plan.text().isBlank()) {
      return List.of();
    }
    return List.of(new PromptSection(
        PracticeChatPromptConstants.SECTION_LEARNER_PROFILE,
        "学习者画像参考",
        PromptSlot.MEMORY_SUMMARY,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.MODEL_GENERATED,
        PromptSensitivity.USER_CONTENT,
        45,
        false,
        "v1",
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.DROP_IF_NEEDED,
        PromptRenderMode.BOUNDED_BLOCK,
        new PromptSourceRef(
            "learner-profile",
            "practice-chat",
            Map.of(
                PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_ENTRY_COUNT, plan.entryCount(),
                PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_TOKEN_ESTIMATE, plan.tokenEstimate(),
                PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_TRIMMED, plan.trimmed())),
        Map.of(TEXT, plan.text())));
  }

  /** 供 conversation 装配器写入不含正文的诊断 metadata。 */
  public Map<String, Object> metadata(
      LearnerProfileRecallSnapshot snapshot,
      PromptAssembly assembly,
      ResolvedSystemPromptSnapshot systemPromptSnapshot
  ) {
    RenderPlan plan = renderPlan(snapshot, systemPromptSnapshot);
    RenderedPromptSection rendered = assembly.renderedSections().stream()
        .filter(section -> PracticeChatPromptConstants.SECTION_LEARNER_PROFILE.equals(section.section().id()))
        .findFirst()
        .orElse(null);
    int tokenEstimate = rendered != null && rendered.included() ? rendered.tokenEstimate() : 0;
    boolean trimmed = plan.trimmed() || (rendered != null && rendered.budgetDecision().truncated());
    return Map.of(
        PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_TOKEN_ESTIMATE, tokenEstimate,
        PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_TRIMMED, trimmed,
        PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_ENTRY_COUNT, plan.entryCount());
  }

  private LearnerProfileRecallSnapshot snapshot(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_LEARNER_PROFILE_SNAPSHOT);
    return value instanceof LearnerProfileRecallSnapshot snapshot ? snapshot : LearnerProfileRecallSnapshot.empty();
  }

  private RenderPlan renderPlan(
      LearnerProfileRecallSnapshot snapshot,
      ResolvedSystemPromptSnapshot systemPromptSnapshot
  ) {
    if (snapshot == null || snapshot.isEmpty()) {
      return new RenderPlan("", 0, false, 0);
    }
    int maxChars = Math.max(1, maxTokenBudget * CHARS_PER_TOKEN - RENDER_CHROME_CHARS);
    StringBuilder text = new StringBuilder(systemPromptSnapshot
        .requireSection(SystemPromptSectionKeys.PRACTICE_LEARNER_PROFILE_BOUNDARY).text());
    boolean trimmed = false;
    for (String snippet : snippets(snapshot)) {
      if (text.length() + snippet.length() <= maxChars) {
        text.append(snippet);
        continue;
      }
      int remaining = maxChars - text.length();
      if (remaining > TRUNCATED_MARKER.length()) {
        int contentLength = remaining - TRUNCATED_MARKER.length();
        text.append(snippet, 0, Math.min(snippet.length(), contentLength)).append(TRUNCATED_MARKER);
      }
      trimmed = true;
      break;
    }
    return new RenderPlan(text.toString(), estimateTokens(text.toString()), trimmed, snapshot.entryCount());
  }

  private ResolvedSystemPromptSnapshot systemPromptSnapshot(PromptAssemblyRequest request) {
    Object value = request.variables().get(PracticeChatPromptConstants.VARIABLE_SYSTEM_PROMPT_SNAPSHOT);
    if (value instanceof ResolvedSystemPromptSnapshot snapshot
        && ManagedSystemPromptDefinitions.PRACTICE_CHAT.typeCode().equals(snapshot.typeCode())) {
      return snapshot;
    }
    // Standalone assembly has no trusted user context and must not enter a user-scoped policy path.
    return ManagedSystemPrompts.defaultRegistry().codeDefaultSnapshot(
        ManagedSystemPromptDefinitions.PRACTICE_CHAT, SystemPromptResolutionSource.CODE_POLICY_UNAVAILABLE);
  }

  private List<String> snippets(LearnerProfileRecallSnapshot snapshot) {
    List<String> snippets = new ArrayList<>();
    snapshot.declaredFacts().forEach(entry -> snippets.add("\n- 用户明确自述 / "
        + entry.identity().dimension().name() + "：" + entry.contentText()));
    snapshot.currentProblemTagAssessments().forEach(assessment -> snippets.add("\n- 当前题目标签能力 / "
        + assessment.tag().value() + "：" + assessment.entry().contentText()));
    snapshot.generalObservations().forEach(entry -> snippets.add("\n- 跨题观察 / "
        + entry.identity().dimension().name() + "：" + entry.contentText()));
    return List.copyOf(snippets);
  }

  private int estimateTokens(String text) {
    int chars = text == null ? 0 : text.length() + RENDER_CHROME_CHARS;
    return chars == 0 ? 0 : Math.max(1, chars / CHARS_PER_TOKEN);
  }

  private record RenderPlan(String text, int tokenEstimate, boolean trimmed, int entryCount) {
  }
}
