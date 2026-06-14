package org.congcong.algomentor.agent.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class AgentToolRegistry {

  private final Map<String, AgentTool> toolsByName;

  private AgentToolRegistry(Collection<AgentTool> tools) {
    Map<String, AgentTool> toolsByName = new LinkedHashMap<>();
    for (AgentTool tool : tools == null ? List.<AgentTool>of() : tools) {
      if (tool == null) {
        throw new IllegalArgumentException("Agent tool must not be null");
      }
      String name = tool.spec().name();
      if (toolsByName.putIfAbsent(name, tool) != null) {
        throw new IllegalArgumentException("Duplicate agent tool: " + name);
      }
    }
    this.toolsByName = Map.copyOf(toolsByName);
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

  public boolean isEmpty() {
    return toolsByName.isEmpty();
  }
}
