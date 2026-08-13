package org.congcong.algomentor.ai.governance.policy.runtime;

/** ai-runtime-settings Redis 负缓存 envelope，不属于 controller API。 */
public record AiRuntimeSettingsCacheEntry(boolean present, AiRuntimeSettings value) {

  public AiRuntimeSettingsCacheEntry {
    if (present != (value != null)) {
      throw new IllegalArgumentException("present and value must agree");
    }
  }

  static AiRuntimeSettingsCacheEntry from(java.util.Optional<AiRuntimeSettings> value) {
    return new AiRuntimeSettingsCacheEntry(value.isPresent(), value.orElse(null));
  }

  java.util.Optional<AiRuntimeSettings> toOptional() {
    return present ? java.util.Optional.of(value) : java.util.Optional.empty();
  }
}
