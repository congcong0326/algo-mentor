package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Practice Chat 经用户确认追加当前题目笔记的独立能力开关。 */
@ConfigurationProperties(prefix = PracticeChatNoteAppendProperties.PREFIX)
public class PracticeChatNoteAppendProperties {

  public static final String PREFIX = "algo-mentor.practice-chat.note-append";

  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
