package org.congcong.algomentor.agent.runtime.definition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;

/** 已生效 Agent Definition 的不可变注册表。 */
public final class AgentDefinitionRegistry {

  private final Map<String, AgentDefinition<?>> definitionsByKey;
  private final List<AgentDefinition<?>> definitions;

  public AgentDefinitionRegistry(Collection<? extends AgentDefinition<?>> definitions) {
    Collection<? extends AgentDefinition<?>> source = definitions == null ? List.of() : definitions;
    Map<String, AgentDefinition<?>> registered = new LinkedHashMap<>();
    for (AgentDefinition<?> definition : source) {
      AgentDefinition<?> candidate = Objects.requireNonNull(definition, "Agent definition must not be null");
      AgentKey<?> key = Objects.requireNonNull(candidate.key(), "Agent definition key must not be null");
      AgentDefinition<?> duplicate = registered.putIfAbsent(key.value(), candidate);
      if (duplicate != null) {
        throw new IllegalArgumentException("Duplicate agent definition key: " + key.value());
      }
    }
    this.definitionsByKey = Map.copyOf(registered);
    this.definitions = List.copyOf(new ArrayList<>(registered.values()));
  }

  public List<AgentDefinition<?>> definitions() {
    return definitions;
  }

  public boolean isEmpty() {
    return definitions.isEmpty();
  }

  /** 通过类型化 key 解析 Definition，并防御装配阶段的输入类型漂移。 */
  public <I> AgentDefinition<I> resolve(AgentKey<I> key) {
    AgentKey<I> requestedKey = Objects.requireNonNull(key, "Agent key must not be null");
    AgentDefinition<?> definition = definitionsByKey.get(requestedKey.value());
    if (definition == null) {
      throw new IllegalArgumentException("Agent definition is not registered: " + requestedKey.value());
    }
    if (!definition.key().equals(requestedKey)) {
      throw new IllegalArgumentException(
          "Agent definition input type does not match key: " + requestedKey.value());
    }
    return castDefinition(definition);
  }

  /** 在 Registry 内完成唯一受控的类型转换后，调用 Definition 组装请求。 */
  public AgentPreparedRequest prepare(AgentInvocation<?> invocation) {
    AgentInvocation<?> candidate = Objects.requireNonNull(invocation, "Agent invocation must not be null");
    return prepare(resolveByInvocation(candidate), candidate);
  }

  private AgentDefinition<?> resolveByInvocation(AgentInvocation<?> invocation) {
    AgentDefinition<?> definition = definitionsByKey.get(invocation.agentKey().value());
    if (definition == null) {
      throw new IllegalArgumentException("Agent definition is not registered: " + invocation.agentKey().value());
    }
    if (!definition.key().equals(invocation.agentKey())) {
      throw new IllegalArgumentException(
          "Agent definition input type does not match key: " + invocation.agentKey().value());
    }
    return definition;
  }

  private <I> AgentPreparedRequest prepare(AgentDefinition<I> definition, AgentInvocation<?> invocation) {
    I input = definition.key().inputType().cast(invocation.input());
    return definition.prepare(input, invocation.context());
  }

  @SuppressWarnings("unchecked")
  private static <I> AgentDefinition<I> castDefinition(AgentDefinition<?> definition) {
    return (AgentDefinition<I>) definition;
  }
}
