package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** PRACTICE_CHAT 的总 Prompt 预算，供 assembly policy 和 profile resolver 共用。 */
@ConfigurationProperties(prefix = PracticeChatPromptProperties.PREFIX)
public class PracticeChatPromptProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.prompt";

  private int totalTokenBudget = 8_000;

  public int getTotalTokenBudget() {
    return totalTokenBudget;
  }

  public void setTotalTokenBudget(int totalTokenBudget) {
    if (totalTokenBudget < 1) {
      throw new IllegalArgumentException("algo-mentor.practice-chat.prompt.total-token-budget must be positive");
    }
    this.totalTokenBudget = totalTokenBudget;
  }
}
