package org.congcong.algomentor.cache.spec;

import java.util.Objects;
import java.util.regex.Pattern;

public record CacheRegionName(String value) {

  private static final Pattern STABLE_KEBAB_CASE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

  public CacheRegionName {
    value = requireStableKebabCase(value, "cache name");
  }

  public static String requireStableKebabCase(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    if (!STABLE_KEBAB_CASE.matcher(value).matches()) {
      throw new IllegalArgumentException(fieldName + " must use lowercase kebab-case");
    }
    return value;
  }
}
