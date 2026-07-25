package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewPromptBuilderTest {

  @Test
  void requiresJudgeFirstHardGatesAndSuboptimalScoreCap() {
    List<LlmMessage> messages = new PracticeCodeReviewPromptBuilder().build(context());

    assertThat(messages).hasSize(2);
    assertThat(messages.get(1).text())
        .contains("先判断 judgeAssessment，再进行分项评分")
        .contains("TLE 时 complexity=0")
        .contains("total<=8；不得给 9 分或 10 分")
        .contains("basis=USER_REPORTED_EXECUTION")
        .contains("n 最大为 100000")
        .contains("expectedTimeComplexity");
  }

  private PracticeTurnContext context() {
    return new PracticeTurnContext(
        7L,
        12L,
        1,
        "two-sum",
        50L,
        701L,
        702L,
        501L,
        "题目要求返回两数下标，n 最大为 100000。",
        "哈希表阶段",
        "class Solution {}",
        "请 Review",
        "",
        "zh-CN");
  }
}
