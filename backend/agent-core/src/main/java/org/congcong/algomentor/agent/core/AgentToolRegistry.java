package org.congcong.algomentor.agent.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class AgentToolRegistry {

  private final Map<String, AgentTool> toolsByName;

  private AgentToolRegistry(Collection<AgentTool> tools) {
    Objects.requireNonNull(tools, "agent tools must not be null");
    Map<String, AgentTool> toolsByName = new LinkedHashMap<>();
    for (AgentTool tool : tools) {
      if (tool == null) {
        throw new IllegalArgumentException("Agent tool must not be null");
      }
      LlmToolSpec spec = Objects.requireNonNull(tool.spec(), "Agent tool spec must not be null");
      String name = spec.name();
      if (toolsByName.putIfAbsent(name, tool) != null) {
        throw new IllegalArgumentException("Duplicate agent tool: " + name);
      }
    }
    this.toolsByName = Collections.unmodifiableMap(new LinkedHashMap<>(toolsByName));
  }

  public static AgentToolRegistry empty() {
    return new AgentToolRegistry(List.of());
  }

  public static AgentToolRegistry of(Collection<AgentTool> tools) {
    return new AgentToolRegistry(tools);
  }

  public Optional<AgentTool> find(String name) {
    return Optional.ofNullable(toolsByName.get(name));
  }

  public List<LlmToolSpec> specs() {
    return toolsByName.values().stream()
        .map(AgentTool::spec)
        .toList();
  }

  /**
   * 创建只包含本次 run 明确允许工具的受限视图。
   *
   * <p>返回值复用当前 Registry 实现和工具实例，不创建第二套全局工具容器。</p>
   */
  public AgentToolRegistry select(Collection<String> toolNames) {
    Objects.requireNonNull(toolNames, "agent tool names must not be null");
    Map<String, AgentTool> selected = new LinkedHashMap<>();
    for (String toolName : toolNames) {
      if (toolName == null || toolName.isBlank()) {
        throw new IllegalArgumentException("Agent tool name must not be blank");
      }
      String normalizedName = toolName.trim();
      if (selected.containsKey(normalizedName)) {
        throw new IllegalArgumentException("Duplicate selected agent tool: " + normalizedName);
      }
      AgentTool tool = find(normalizedName)
          .orElseThrow(() -> new IllegalArgumentException("Unknown agent tool: " + normalizedName));
      selected.put(normalizedName, tool);
    }
    return new AgentToolRegistry(selected.values());
  }

  public boolean isEmpty() {
    return toolsByName.isEmpty();
  }
}
