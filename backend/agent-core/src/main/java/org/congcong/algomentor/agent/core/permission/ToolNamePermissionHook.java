package org.congcong.algomentor.agent.core.permission;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolNamePermissionHook implements AgentToolPermissionHook {

  public static final int DEFAULT_ORDER = 100;
  public static final String POLICY_SOURCE = "tool-name-policy";
  public static final String PREVIEW_TOOL_NAME = "toolName";

  private static final String DEFAULT_DENY_REASON = "tool_name_policy_denied";
  private static final String DEFAULT_ASK_REASON = "该工具执行前需要用户确认。";

  private final int order;
  private final Map<String, ToolNamePermissionRule> rules;

  public ToolNamePermissionHook(Map<String, AgentToolPermissionBehavior> toolBehaviors) {
    this(DEFAULT_ORDER, toRules(toolBehaviors));
  }

  public ToolNamePermissionHook(int order, Map<String, ToolNamePermissionRule> rules) {
    this.order = order;
    this.rules = copyRules(rules);
  }

  public static ToolNamePermissionHook fromBehaviors(
      int order,
      Map<String, AgentToolPermissionBehavior> toolBehaviors
  ) {
    return new ToolNamePermissionHook(order, toRules(toolBehaviors));
  }

  @Override
  public int order() {
    return order;
  }

  @Override
  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }

    String toolName = check.toolCall().name();
    ToolNamePermissionRule rule = rules.get(toolName);
    if (rule == null) {
      return AgentToolPermissionDecisionPlan.passthrough();
    }

    return switch (rule.behavior()) {
      case ALLOW -> AgentToolPermissionDecisionPlan.allow(rule.policySource());
      case DENY -> AgentToolPermissionDecisionPlan.deny(denyReason(rule), rule.policySource());
      case ASK -> AgentToolPermissionDecisionPlan.ask(
          askDisplayName(rule, check),
          askReason(rule),
          askPreview(rule, toolName),
          rule.policySource());
      case PASSTHROUGH -> AgentToolPermissionDecisionPlan.passthrough();
    };
  }

  public Map<String, ToolNamePermissionRule> rules() {
    return rules;
  }

  private static Map<String, ToolNamePermissionRule> toRules(
      Map<String, AgentToolPermissionBehavior> toolBehaviors
  ) {
    if (toolBehaviors == null || toolBehaviors.isEmpty()) {
      return Map.of();
    }
    Map<String, ToolNamePermissionRule> rules = new LinkedHashMap<>();
    toolBehaviors.forEach((toolName, behavior) ->
        rules.put(toolName, new ToolNamePermissionRule(behavior)));
    return rules;
  }

  private static Map<String, ToolNamePermissionRule> copyRules(
      Map<String, ToolNamePermissionRule> rules
  ) {
    if (rules == null || rules.isEmpty()) {
      return Map.of();
    }
    Map<String, ToolNamePermissionRule> copy = new LinkedHashMap<>();
    rules.forEach((toolName, rule) -> {
      requireText(toolName, "Agent tool permission tool name must not be blank");
      if (rule == null) {
        throw new IllegalArgumentException("Agent tool permission tool name rule must not be null");
      }
      copy.put(toolName, rule);
    });
    return Map.copyOf(copy);
  }

  private static String askDisplayName(
      ToolNamePermissionRule rule,
      AgentToolPermissionCheck check
  ) {
    if (hasText(rule.displayName())) {
      return rule.displayName();
    }
    return check.tool().spec().description();
  }

  private static String askReason(ToolNamePermissionRule rule) {
    if (hasText(rule.reason())) {
      return rule.reason();
    }
    return DEFAULT_ASK_REASON;
  }

  private static Map<String, Object> askPreview(ToolNamePermissionRule rule, String toolName) {
    if (!rule.preview().isEmpty()) {
      return rule.preview();
    }
    return Map.of(PREVIEW_TOOL_NAME, toolName);
  }

  private static String denyReason(ToolNamePermissionRule rule) {
    if (hasText(rule.reason())) {
      return rule.reason();
    }
    return DEFAULT_DENY_REASON;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static void requireText(String value, String message) {
    if (!hasText(value)) {
      throw new IllegalArgumentException(message);
    }
  }

  public record ToolNamePermissionRule(
      AgentToolPermissionBehavior behavior,
      String displayName,
      String reason,
      Map<String, Object> preview,
      String policySource
  ) {

    public ToolNamePermissionRule(AgentToolPermissionBehavior behavior) {
      this(behavior, null, null, Map.of(), POLICY_SOURCE);
    }

    public ToolNamePermissionRule {
      if (behavior == null) {
        throw new IllegalArgumentException("Agent tool permission behavior must not be null");
      }
      preview = preview == null ? Map.of() : Map.copyOf(preview);
      if (!hasText(policySource)) {
        policySource = POLICY_SOURCE;
      }
    }

    public static ToolNamePermissionRule allow() {
      return new ToolNamePermissionRule(AgentToolPermissionBehavior.ALLOW);
    }

    public static ToolNamePermissionRule deny(String reason) {
      return new ToolNamePermissionRule(
          AgentToolPermissionBehavior.DENY,
          null,
          reason,
          Map.of(),
          POLICY_SOURCE);
    }

    public static ToolNamePermissionRule ask(
        String displayName,
        String reason,
        Map<String, Object> preview
    ) {
      return new ToolNamePermissionRule(
          AgentToolPermissionBehavior.ASK,
          displayName,
          reason,
          preview,
          POLICY_SOURCE);
    }

    public static ToolNamePermissionRule passthrough() {
      return new ToolNamePermissionRule(AgentToolPermissionBehavior.PASSTHROUGH);
    }
  }
}
