package org.congcong.algomentor.api.config;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

/** 启动时提示学习计划 Redis Stream 仍在使用阶段一配置前缀。 */
public final class LearningPlanRealtimeStreamLegacyConfigurationWarning implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanRealtimeStreamLegacyConfigurationWarning.class);
  private static final List<String> PROPERTY_NAMES = List.of(
      "enabled", "host", "port", "database", "username", "password", "ssl-enabled",
      "command-timeout", "connect-timeout", "shutdown-timeout", "read-block",
      "max-read-connections", "active-retention", "completed-retention");

  private final Environment environment;

  public LearningPlanRealtimeStreamLegacyConfigurationWarning(Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  @Override
  public void run(ApplicationArguments args) {
    if (hasLegacyConfiguration()) {
      log.warn("Deprecated learning-plan Redis Stream configuration detected. Migrate from {} "
              + "to {}; canonical settings take precedence.",
          MentorConfigurationKeys.LEARNING_PLAN_GENERATION_REALTIME_STREAM_PREFIX,
          MentorConfigurationKeys.LEARNING_PLAN_REALTIME_STREAM_PREFIX);
    }
  }

  boolean hasLegacyConfiguration() {
    return PROPERTY_NAMES.stream().anyMatch(this::hasLegacyProperty);
  }

  private boolean hasLegacyProperty(String name) {
    return environment.containsProperty(
        MentorConfigurationKeys.LEARNING_PLAN_GENERATION_REALTIME_STREAM_PREFIX + "." + name)
        || environment.containsProperty("LEARNING_PLAN_GENERATION_REALTIME_STREAM_"
            + name.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
  }
}
