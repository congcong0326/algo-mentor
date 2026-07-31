package org.congcong.algomentor.mentor.application.profile.recall;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 以完整索引项和完整 claim 为单位，在独立预算内生成 Practice Chat bootstrap。 */
public final class LearnerMemoryRecallBootstrapBuilder {

  private static final int CHARS_PER_TOKEN = 4;
  private static final int RENDER_CHROME_CHARS = 128;
  private static final DateTimeFormatter UPDATED_AT_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
      .withZone(ZoneOffset.UTC);

  private final int tokenBudget;

  public LearnerMemoryRecallBootstrapBuilder(int tokenBudget) {
    if (tokenBudget < 1 || tokenBudget > LearnerMemoryRecallContracts.MAX_BOOTSTRAP_TOKEN_BUDGET) {
      throw new IllegalArgumentException("Learner memory bootstrap token budget is outside the allowed range");
    }
    this.tokenBudget = tokenBudget;
  }

  public Bootstrap build(LearnerMemoryRecallSnapshot snapshot, String boundary) {
    if (snapshot == null || snapshot.claimCount() == 0) {
      return Bootstrap.empty();
    }
    int maxChars = Math.max(0, tokenBudget * CHARS_PER_TOKEN - RENDER_CHROME_CHARS);
    String header = header(boundary);
    if (header.length() > maxChars) {
      return Bootstrap.empty();
    }
    StringBuilder text = new StringBuilder(header);
    boolean trimmed = false;
    for (String indexItem : indexItems(snapshot.sections())) {
      if (!appendWhole(text, indexItem, maxChars)) {
        trimmed = true;
        return new Bootstrap(text.toString(), estimateTokens(text), 0, true);
      }
    }
    int includedClaims = 0;
    for (LearnerMemoryRecallSnapshot.Statement statement : snapshot.directHits()) {
      if (!appendWhole(text, directHitItem(statement), maxChars)) {
        trimmed = true;
        break;
      }
      includedClaims++;
    }
    trimmed = trimmed || includedClaims < snapshot.directHits().size();
    return new Bootstrap(text.toString(), estimateTokens(text), includedClaims, trimmed);
  }

  private String header(String boundary) {
    String normalizedBoundary = boundary == null ? "" : boundary.strip();
    String fixedBoundary = "以下内容是长期记忆的有界视图；Prompt 未出现不代表记忆不存在。若本 run 工具列表提供 "
        + "search_learner_memory、read_learner_memory_section 或 get_learner_memory_evidence，可在问题相关且当前内容不足时自主查询；"
        + "无关时不要全量检查。";
    return (normalizedBoundary.isBlank() ? fixedBoundary : normalizedBoundary + "\n" + fixedBoundary) + "\n记忆主题索引：";
  }

  private List<String> indexItems(List<LearnerMemoryRecallSnapshot.Section> sections) {
    List<String> items = new ArrayList<>();
    for (LearnerMemoryRecallSnapshot.Section section : sections) {
      String updatedAt = section.latestUpdatedAt() == null ? "无" : UPDATED_AT_FORMATTER.format(section.latestUpdatedAt());
      items.add("\n- [" + section.sectionRef() + "] " + section.title() + "："
          + section.statements().size() + " 条，最近更新 " + updatedAt
          + "，当前题命中 " + section.currentProblemMatchCount() + " 条");
    }
    return List.copyOf(items);
  }

  private String directHitItem(LearnerMemoryRecallSnapshot.Statement statement) {
    return "\n- [" + statement.statementRef() + "] " + statement.claim().claimText()
        + "\n  来源：" + statement.sourceSummary();
  }

  private boolean appendWhole(StringBuilder target, String item, int maxChars) {
    Objects.requireNonNull(item, "item");
    if (target.length() + item.length() > maxChars) {
      return false;
    }
    target.append(item);
    return true;
  }

  private int estimateTokens(CharSequence text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return Math.max(1, (text.length() + RENDER_CHROME_CHARS) / CHARS_PER_TOKEN);
  }

  public record Bootstrap(String text, int tokenEstimate, int directHitCount, boolean trimmed) {

    public Bootstrap {
      text = text == null ? "" : text;
      if (tokenEstimate < 0 || directHitCount < 0) {
        throw new IllegalArgumentException("Learner memory bootstrap counters must not be negative");
      }
    }

    static Bootstrap empty() {
      return new Bootstrap("", 0, 0, false);
    }
  }
}
