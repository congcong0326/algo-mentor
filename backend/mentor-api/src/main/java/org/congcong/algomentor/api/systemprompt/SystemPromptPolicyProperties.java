package org.congcong.algomentor.api.systemprompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 系统提示词数据库策略适配的总开关，关闭时始终使用代码默认值。 */
@ConfigurationProperties(SystemPromptPolicyProperties.PREFIX)
public class SystemPromptPolicyProperties {

  public static final String PREFIX = "algo-mentor.system-prompt.policy";
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
