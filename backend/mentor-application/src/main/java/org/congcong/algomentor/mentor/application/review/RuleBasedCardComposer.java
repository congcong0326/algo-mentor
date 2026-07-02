package org.congcong.algomentor.mentor.application.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RuleBasedCardComposer {

  private static final int DEFAULT_MAX_INPUT_CHARS = 400;

  public ReviewCard compose(MistakeNote note) {
    return compose(note, CardVariant.RULE_BASED);
  }

  public ReviewCard staticCard(MistakeNote note) {
    return compose(note, CardVariant.STATIC);
  }

  private ReviewCard compose(MistakeNote note, CardVariant variant) {
    String difficulty = sourceText(note, "difficulty", "UNKNOWN").toUpperCase(Locale.ROOT);
    boolean hardOrLapsed = "HARD".equals(difficulty) || note.scheduling().lapses() >= 2;
    boolean easyMastered = "EASY".equals(difficulty) && note.lastGrade() != null
        && note.lastGrade().q() >= ReviewGrade.MASTERED.q();

    List<ReviewCardPrompt> prompts = new ArrayList<>();
    prompts.add(new ReviewCardPrompt("algo_choice", "你会用什么算法？为什么？", null));
    prompts.add(new ReviewCardPrompt("key_step", "关键实现点（一两句即可）", "别写完整代码"));
    prompts.add(new ReviewCardPrompt("complexity", "时间/空间复杂度", null));
    prompts.add(new ReviewCardPrompt("edge_case", "最容易漏掉的边界条件是什么？", null));

    if (variant == CardVariant.RULE_BASED && easyMastered) {
      prompts = List.of(prompts.get(1), prompts.get(2));
    } else if (variant == CardVariant.RULE_BASED && !hardOrLapsed) {
      prompts = prompts.subList(0, 3);
    }

    ReviewCardScaffold scaffold = hardOrLapsed || variant == CardVariant.STATIC
        ? new ReviewCardScaffold("""
            1. 我的思路是：
            2. 关键步骤：
            3. 复杂度：
            4. 边界与坑：
            """.stripIndent().trim(), DEFAULT_MAX_INPUT_CHARS)
        : null;

    return new ReviewCard(
        variant,
        new ProblemRef(note.problemSlug(), sourceText(note, "titleCn", note.problemSlug()), difficulty),
        contextSummary(note),
        prompts,
        scaffold,
        MistakeReviewConstants.REVEAL_HIDE_PREVIOUS,
        hardOrLapsed ? "HEAVY" : "LIGHT");
  }

  private String contextSummary(MistakeNote note) {
    Object score = note.sourceDetail().get("latestReviewScore");
    Object deductions = note.sourceDetail().get("deductionReasons");
    if (score == null && deductions == null) {
      return "请先独立复述思路，再查看任何提示或旧代码。";
    }
    return "上次 Review：%s；扣分点：%s".formatted(
        score == null ? "未知" : score,
        deductions == null ? "未记录" : deductions);
  }

  private String sourceText(MistakeNote note, String key, String fallback) {
    Object value = note.sourceDetail().get(key);
    if (value == null || value.toString().isBlank()) {
      return fallback;
    }
    return value.toString();
  }
}
