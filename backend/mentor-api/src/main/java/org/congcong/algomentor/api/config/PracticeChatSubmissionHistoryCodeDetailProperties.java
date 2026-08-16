package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 历史正式提交源码 detail Tool 的二级开关，默认关闭且依赖总开关。 */
@ConfigurationProperties(prefix = PracticeChatSubmissionHistoryCodeDetailProperties.PREFIX)
public class PracticeChatSubmissionHistoryCodeDetailProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.submission-history-code-detail";

  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
