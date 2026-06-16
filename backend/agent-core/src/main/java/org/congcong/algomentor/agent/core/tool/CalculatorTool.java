package org.congcong.algomentor.agent.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

/**
 * 受限算术计算工具，仅支持数字、括号和 + - * / 运算。
 */
public final class CalculatorTool implements AgentTool {

  public static final String NAME = "calculator";

  private static final int MAX_EXPRESSION_LENGTH = 200;
  private static final MathContext MATH_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      NAME,
      "Evaluate a basic arithmetic expression using numbers, parentheses, and + - * / operators.",
      inputSchema(),
      true);

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String expression = expressionFrom(arguments);
    BigDecimal value = new ExpressionParser(expression).parse();

    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("expression", expression);
    result.put("value", normalize(value));
    return result;
  }

  private static String expressionFrom(JsonNode arguments) {
    if (arguments == null || !arguments.isObject()) {
      throw invalidExpression("Calculator arguments must be a JSON object");
    }
    JsonNode expressionNode = arguments.get("expression");
    if (expressionNode == null || !expressionNode.isTextual()) {
      throw invalidExpression("Calculator expression must be a string");
    }
    String expression = expressionNode.asText().trim();
    if (expression.isBlank()) {
      throw invalidExpression("Calculator expression must not be blank");
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      throw invalidExpression("Calculator expression is too long");
    }
    return expression;
  }

  private static String normalize(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    if (stripped.scale() < 0) {
      stripped = stripped.setScale(0);
    }
    return stripped.toPlainString();
  }

  private static AgentException invalidExpression(String message) {
    return new AgentException(
        AgentErrorCode.TOOL_EXECUTION_FAILED,
        message,
        false,
        Map.of("toolName", NAME),
        null);
  }

  private static JsonNode inputSchema() {
    ObjectNode expression = JsonNodeFactory.instance.objectNode()
        .put("type", "string")
        .put("description", "Arithmetic expression to evaluate, for example: (12 + 8) / 5.");

    ObjectNode properties = JsonNodeFactory.instance.objectNode()
        .set("expression", expression);

    ObjectNode schema = JsonNodeFactory.instance.objectNode()
        .put("type", "object");
    schema.set("properties", properties);
    schema.set("required", JsonNodeFactory.instance.arrayNode().add("expression"));
    schema.put("additionalProperties", false);
    return schema;
  }

  private static final class ExpressionParser {
    private final String input;
    private int position;

    private ExpressionParser(String input) {
      this.input = input;
    }

    private BigDecimal parse() {
      BigDecimal result = parseExpression();
      skipWhitespace();
      if (!isEnd()) {
        throw invalidExpression("Unsupported calculator token at position " + position);
      }
      return result;
    }

    private BigDecimal parseExpression() {
      BigDecimal value = parseTerm();
      while (true) {
        skipWhitespace();
        if (consume('+')) {
          value = value.add(parseTerm(), MATH_CONTEXT);
        } else if (consume('-')) {
          value = value.subtract(parseTerm(), MATH_CONTEXT);
        } else {
          return value;
        }
      }
    }

    private BigDecimal parseTerm() {
      BigDecimal value = parseFactor();
      while (true) {
        skipWhitespace();
        if (consume('*')) {
          value = value.multiply(parseFactor(), MATH_CONTEXT);
        } else if (consume('/')) {
          BigDecimal divisor = parseFactor();
          if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw invalidExpression("Calculator expression divides by zero");
          }
          value = value.divide(divisor, MATH_CONTEXT);
        } else {
          return value;
        }
      }
    }

    private BigDecimal parseFactor() {
      skipWhitespace();
      if (consume('+')) {
        return parseFactor();
      }
      if (consume('-')) {
        return parseFactor().negate(MATH_CONTEXT);
      }
      if (consume('(')) {
        BigDecimal value = parseExpression();
        skipWhitespace();
        if (!consume(')')) {
          throw invalidExpression("Calculator expression has unmatched parentheses");
        }
        return value;
      }
      return parseNumber();
    }

    private BigDecimal parseNumber() {
      skipWhitespace();
      int start = position;
      boolean hasDigit = false;
      boolean hasDecimalPoint = false;
      while (!isEnd()) {
        char ch = input.charAt(position);
        if (Character.isDigit(ch)) {
          hasDigit = true;
          position++;
        } else if (ch == '.' && !hasDecimalPoint) {
          hasDecimalPoint = true;
          position++;
        } else {
          break;
        }
      }
      if (!hasDigit) {
        throw invalidExpression("Calculator expression expected a number at position " + start);
      }
      try {
        return new BigDecimal(input.substring(start, position), MATH_CONTEXT);
      } catch (NumberFormatException ex) {
        throw invalidExpression("Calculator expression contains an invalid number");
      }
    }

    private boolean consume(char expected) {
      if (!isEnd() && input.charAt(position) == expected) {
        position++;
        return true;
      }
      return false;
    }

    private void skipWhitespace() {
      while (!isEnd() && Character.isWhitespace(input.charAt(position))) {
        position++;
      }
    }

    private boolean isEnd() {
      return position >= input.length();
    }
  }
}
