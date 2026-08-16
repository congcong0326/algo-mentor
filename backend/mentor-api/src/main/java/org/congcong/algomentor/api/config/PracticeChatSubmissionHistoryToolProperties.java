package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 历史正式提交 overview/list Tool 的总开关，默认保持关闭。 */
@ConfigurationProperties(prefix = PracticeChatSubmissionHistoryToolProperties.PREFIX)
public class PracticeChatSubmissionHistoryToolProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.submission-history-tool";

  private boolean enabled;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
