package org.congcong.algomentor.agent.core.prompt;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultPromptRenderer implements PromptRenderer {

  private static final String TEXT_VARIABLE = "text";
  private static final String BOUNDARY_VARIABLE = "boundary";
  private static final String TRUNCATED_MARKER = "\n[truncated]";

  @Override
  public RenderedPromptSection render(PromptSection section, PromptBudgetDecision budgetDecision) {
    String renderedText = applyBudget(renderFullText(section), budgetDecision);
    return new RenderedPromptSection(
        section,
        renderedText,
        renderedText.length(),
        estimateTokens(renderedText),
        budgetDecision);
  }

  private String renderFullText(PromptSection section) {
    String text = textVariable(section.variables());
    return switch (section.renderMode()) {
      case PLAIN_TEXT -> text;
      case MARKDOWN -> renderMarkdown(section, text);
      case BOUNDED_BLOCK -> renderBoundedBlock(section, text);
    };
  }

  private String renderMarkdown(PromptSection section, String text) {
    if (text.isBlank()) {
      return "# " + section.title();
    }
    return "# " + section.title() + "\n\n" + text;
  }

  private String renderBoundedBlock(PromptSection section, String text) {
    String boundary = boundary(section);
    String endTag = "</" + boundary + ">";
    String escaped = text.replace(endTag, "<\\/" + boundary + ">");
    return "# " + section.title() + "\n" + "<" + boundary + ">\n" + escaped + "\n" + endTag;
  }

  private String applyBudget(String text, PromptBudgetDecision decision) {
    return switch (decision.action()) {
      case KEEP -> text;
      case DROP, FAIL_REQUIRED -> "";
      case TRUNCATE, SUMMARIZE -> truncate(text, decision.tokenLimit());
      case EXTRACT -> extract(text, decision.tokenLimit());
    };
  }

  private String truncate(String text, int tokenLimit) {
    if (text.isBlank() || tokenLimit <= 0) {
      return "";
    }
    int charLimit = tokenLimit * 4;
    if (text.length() <= charLimit) {
      return text;
    }
    if (charLimit <= TRUNCATED_MARKER.length()) {
      return text.substring(0, charLimit);
    }
    return text.substring(0, charLimit - TRUNCATED_MARKER.length()) + TRUNCATED_MARKER;
  }

  private String extract(String text, int tokenLimit) {
    if (text.isBlank() || tokenLimit <= 0) {
      return "";
    }
    List<String> importantLines = text.lines()
        .filter(DefaultPromptRenderer::isImportantLine)
        .collect(Collectors.toList());
    if (importantLines.isEmpty()) {
      return truncate(text, tokenLimit);
    }
    return truncate(String.join("\n", importantLines), tokenLimit);
  }

  private static boolean isImportantLine(String line) {
    String value = line.toLowerCase(Locale.ROOT);
    return value.contains("constraint")
        || value.contains("example")
        || value.contains("input")
        || value.contains("output")
        || value.contains("error")
        || value.contains("wa")
        || value.contains("tle")
        || value.contains("约束")
        || value.contains("示例")
        || value.contains("输入")
        || value.contains("输出")
        || value.contains("错误");
  }

  private static String textVariable(Map<String, Object> variables) {
    Object value = variables.get(TEXT_VARIABLE);
    if (value instanceof String text) {
      return text;
    }
    return "";
  }

  private static String boundary(PromptSection section) {
    Object configured = section.variables().get(BOUNDARY_VARIABLE);
    if (configured instanceof String value && !value.isBlank()) {
      return sanitizeBoundary(value);
    }
    return sanitizeBoundary(section.id());
  }

  private static String sanitizeBoundary(String value) {
    String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
    if (normalized.isBlank()) {
      return "prompt_section";
    }
    return normalized;
  }

  static int estimateTokens(String text) {
    if (text == null || text.isBlank()) {
      return 0;
    }
    return Math.max(1, text.length() / 4);
  }
}
