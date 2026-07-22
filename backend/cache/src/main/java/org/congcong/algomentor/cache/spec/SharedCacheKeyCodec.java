package org.congcong.algomentor.cache.spec;

import java.util.Objects;

/** 将共享缓存业务 key 编码为稳定且不含敏感原文的失效事件 token。 */
@FunctionalInterface
public interface SharedCacheKeyCodec<K> {

  int MAX_TOKEN_LENGTH = 200;

  String encode(K key);

  static String requireValidToken(String keyToken) {
    Objects.requireNonNull(keyToken, "key token must not be null");
    if (keyToken.isEmpty() || keyToken.length() > MAX_TOKEN_LENGTH) {
      throw new IllegalArgumentException("key token must contain between 1 and 200 characters");
    }
    for (int index = 0; index < keyToken.length(); index++) {
      char character = keyToken.charAt(index);
      if (Character.isWhitespace(character) || Character.isISOControl(character)) {
        throw new IllegalArgumentException("key token must not contain whitespace or control characters");
      }
    }
    return keyToken;
  }
}
