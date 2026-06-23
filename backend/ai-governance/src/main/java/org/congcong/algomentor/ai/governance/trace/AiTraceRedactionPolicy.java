package org.congcong.algomentor.ai.governance.trace;

import java.util.Set;

public class AiTraceRedactionPolicy {

  public String policyVersion() {
    return "agent-trace-redaction-v1";
  }

  public Set<String> sensitiveFieldHints() {
    return Set.of(
        "apikey",
        "api_key",
        "api-key",
        "authorization",
        "cookie",
        "set-cookie",
        "jwt",
        "bearer",
        "token",
        "access_token",
        "refresh_token",
        "oauth",
        "password",
        "passwd",
        "secret",
        "client_secret",
        "databasepassword",
        "database_password",
        "db_password",
        "openai_api_key");
  }
}
