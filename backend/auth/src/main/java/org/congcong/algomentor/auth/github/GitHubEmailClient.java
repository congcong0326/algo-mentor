package org.congcong.algomentor.auth.github;

import java.util.Optional;

/** 使用 GitHub OAuth access token 读取当前用户已验证的主邮箱。 */
public interface GitHubEmailClient {

  Optional<String> findPrimaryVerifiedEmail(String accessToken);
}
