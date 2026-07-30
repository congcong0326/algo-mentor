package org.congcong.algomentor.auth.github;

/** GitHub OAuth2 用户资料查询使用的稳定协议常量。 */
public final class GitHubOAuthConstants {

  public static final String EMAILS_URI = "https://api.github.com/user/emails";
  public static final String ACCEPT_HEADER_VALUE = "application/vnd.github+json";
  public static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
  public static final String API_VERSION = "2022-11-28";
  public static final String USER_AGENT = "algo-mentor";
  public static final String READ_USER_SCOPE = "read:user";
  public static final String USER_EMAIL_SCOPE = "user:email";

  private GitHubOAuthConstants() {
  }
}
