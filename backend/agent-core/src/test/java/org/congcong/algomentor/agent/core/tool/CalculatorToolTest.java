package org.congcong.algomentor.agent.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.junit.jupiter.api.Test;

class CalculatorToolTest {

  private final CalculatorTool tool = new CalculatorTool();

  @Test
  void exposesStrictCalculatorSpec() {
    assertThat(tool.spec().name()).isEqualTo("calculator");
    assertThat(tool.spec().strict()).isTrue();
    assertThat(tool.spec().inputSchema().get("required").get(0).asText()).isEqualTo("expression");
    assertThat(tool.spec().inputSchema().get("additionalProperties").asBoolean()).isFalse();
  }

  @Test
  void evaluatesArithmeticExpressionWithPrecedenceAndParentheses() {
    var arguments = JsonNodeFactory.instance.objectNode()
        .put("expression", "(12 + 8) / 5 + 2 * 3");

    var result = tool.execute(arguments, null);

    assertThat(result.get("expression").asText()).isEqualTo("(12 + 8) / 5 + 2 * 3");
    assertThat(result.get("value").asText()).isEqualTo("10");
  }

  @Test
  void evaluatesDecimalAndUnaryOperators() {
    var arguments = JsonNodeFactory.instance.objectNode()
        .put("expression", "-1.5 + +2.25 * 4");

    var result = tool.execute(arguments, null);

    assertThat(result.get("value").asText()).isEqualTo("7.5");
  }

  @Test
  void rejectsDivideByZero() {
    var arguments = JsonNodeFactory.instance.objectNode()
        .put("expression", "1 / (2 - 2)");

    assertThatThrownBy(() -> tool.execute(arguments, null))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata()).containsEntry("toolName", "calculator");
          assertThat(exception).hasMessageContaining("divides by zero");
        });
  }

  @Test
  void rejectsUnsupportedTokens() {
    var arguments = JsonNodeFactory.instance.objectNode()
        .put("expression", "Math.pow(2, 3)");

    assertThatThrownBy(() -> tool.execute(arguments, null))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception).hasMessageContaining("expected a number");
        });
  }
}
