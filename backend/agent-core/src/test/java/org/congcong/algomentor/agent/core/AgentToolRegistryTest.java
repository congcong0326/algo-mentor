package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {

  @Test
  void rejectsNullToolCollection() {
    assertThatThrownBy(() -> AgentToolRegistry.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("agent tools must not be null");
  }

  @Test
  void selectsToolsInDefinitionOrder() {
    AgentToolRegistry registry = AgentToolRegistry.of(List.of(tool("lookup"), tool("calculator")));

    AgentToolRegistry selected = registry.select(List.of("calculator", "lookup"));

    assertThat(selected.specs()).extracting(LlmToolSpec::name).containsExactly("calculator", "lookup");
  }

  @Test
  void rejectsBlankDuplicateAndUnknownSelectedToolNames() {
    AgentToolRegistry registry = AgentToolRegistry.of(List.of(tool("lookup")));

    assertThatThrownBy(() -> registry.select(List.of(" ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent tool name must not be blank");
    assertThatThrownBy(() -> registry.select(List.of("lookup", "lookup")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate selected agent tool: lookup");
    assertThatThrownBy(() -> registry.select(List.of("missing")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown agent tool: missing");
  }

  private static AgentTool tool(String name) {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(name, name + " tool", JsonNodeFactory.instance.objectNode(), true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return JsonNodeFactory.instance.nullNode();
      }
    };
  }
}
