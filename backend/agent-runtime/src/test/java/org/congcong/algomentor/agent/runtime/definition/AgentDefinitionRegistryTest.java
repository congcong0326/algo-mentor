package org.congcong.algomentor.agent.runtime.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class AgentDefinitionRegistryTest {

  @Test
  void acceptsAnEmptyDefinitionCollection() {
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(List.of());

    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.definitions()).isEmpty();
  }

  @Test
  void rejectsDuplicateStableKeys() {
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(List.of(definition("topic")));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentDefinitionRegistry(List.of(definition("topic"), definition("topic"))))
        .withMessage("Duplicate agent definition key: topic");
    assertThat(registry.definitions()).singleElement().isSameAs(registry.resolve(new AgentKey<>("topic", String.class)));
  }

  @Test
  void rejectsDefinitionsWithoutAnExecutionGroup() {
    AgentDefinition<String> missingGroup = new AgentDefinition<>() {
      @Override
      public AgentKey<String> key() {
        return new AgentKey<>("missing-group", String.class);
      }

      @Override
      public AgentExecutionGroup executionGroup() {
        return null;
      }

      @Override
      public AgentLoopPolicy loopPolicy() {
        return new AgentLoopPolicy(1);
      }

      @Override
      public AgentOutputContract outputContract() {
        return AgentOutputContract.defaults();
      }

      @Override
      public AgentPreparedRequest prepare(String input, AgentInvocationContext context) {
        throw new UnsupportedOperationException();
      }
    };

    assertThatThrownBy(() -> new AgentDefinitionRegistry(List.of(missingGroup)))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Agent definition execution group must not be null: missing-group");
  }

  @Test
  void rejectsARequestedKeyWithTheWrongInputType() {
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(List.of(definition("topic")));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.resolve(new AgentKey<>("topic", Integer.class)))
        .withMessage("Agent definition input type does not match key: topic");
  }

  @Test
  void keepsDefinitionCollectionsAndPreparedRequestContentsImmutable() {
    List<AgentDefinition<?>> definitions = new ArrayList<>();
    definitions.add(definition("topic"));
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(definitions);
    definitions.clear();

    AgentPreparedRequest prepared = registry.prepare(new AgentInvocation<>(
        new AgentKey<>("topic", String.class),
        "binary search",
        context()));

    assertThat(registry.definitions()).hasSize(1);
    assertThatThrownBy(() -> registry.definitions().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> prepared.messages().add(LlmMessage.user("later")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> prepared.metadata().put("later", true))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsAnInvocationWhoseKeyConflictsWithTheRegisteredDefinition() {
    AgentDefinitionRegistry registry = new AgentDefinitionRegistry(List.of(definition("topic")));
    AgentInvocation<?> invalidInvocation = new AgentInvocation(
        new AgentKey("topic", Integer.class),
        42,
        context());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.prepare(invalidInvocation))
        .withMessage("Agent definition input type does not match key: topic");
  }

  private static AgentDefinition<String> definition(String key) {
    return new AgentDefinition<>() {
      private final AgentKey<String> agentKey = new AgentKey<>(key, String.class);

      @Override
      public AgentKey<String> key() {
        return agentKey;
      }

      @Override
      public AgentExecutionGroup executionGroup() {
        return AgentExecutionGroup.PRACTICE;
      }

      @Override
      public AgentLoopPolicy loopPolicy() {
        return new AgentLoopPolicy(1);
      }

      @Override
      public AgentOutputContract outputContract() {
        return AgentOutputContract.defaults();
      }

      @Override
      public AgentPreparedRequest prepare(String input, AgentInvocationContext context) {
        return new AgentPreparedRequest(
            List.of(LlmMessage.user(input)),
            Map.of("userId", context.userId()),
            AgentExecutionOptions.defaults());
      }
    };
  }

  private static AgentInvocationContext context() {
    return new AgentInvocationContext(
        7L,
        AgentInvocationMode.USER_ENTRY,
        "idem-1",
        null,
        null,
        12,
        false);
  }
}
