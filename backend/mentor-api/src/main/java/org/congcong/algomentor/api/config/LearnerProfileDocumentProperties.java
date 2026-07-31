package org.congcong.algomentor.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 画像文档 statement ref 的独立签名配置，不复用客户端可控值。 */
@ConfigurationProperties(prefix = MentorConfigurationKeys.LEARNER_PROFILE_DOCUMENT_PREFIX)
public class LearnerProfileDocumentProperties {

  private String statementRefHmacSecret;

  public String getStatementRefHmacSecret() {
    return statementRefHmacSecret;
  }

  public void setStatementRefHmacSecret(String statementRefHmacSecret) {
    this.statementRefHmacSecret = statementRefHmacSecret;
  }
}
