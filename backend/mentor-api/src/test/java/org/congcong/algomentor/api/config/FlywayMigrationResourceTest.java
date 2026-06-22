package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlywayMigrationResourceTest {

  private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V([0-9][0-9._]*)__.+\\.sql$");
  private static final List<String> MIGRATION_RESOURCE_PATTERNS = List.of(
      "classpath*:db/migration/*.sql",
      "classpath*:db/migration/**/*.sql");

  @Test
  void flywayMigrationVersionsAreUniqueAcrossConfiguredLocations() throws IOException {
    Map<String, List<String>> migrationsByVersion = new LinkedHashMap<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    for (String resourcePattern : MIGRATION_RESOURCE_PATTERNS) {
      for (Resource resource : resolver.getResources(resourcePattern)) {
        String filename = resource.getFilename();
        if (filename == null) {
          continue;
        }
        Matcher matcher = VERSIONED_MIGRATION.matcher(filename);
        if (!matcher.matches()) {
          continue;
        }
        migrationsByVersion.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>())
            .add(resource.getURL().toString());
      }
    }

    Map<String, List<String>> duplicates = new LinkedHashMap<>();
    migrationsByVersion.forEach((version, resources) -> {
      List<String> uniqueResources = resources.stream().distinct().toList();
      if (uniqueResources.size() > 1) {
        duplicates.put(version, uniqueResources);
      }
    });

    assertThat(duplicates)
        .as("Flyway scans migration locations as one version namespace: %s",
            Arrays.toString(MIGRATION_RESOURCE_PATTERNS.toArray()))
        .isEmpty();
    assertThat(migrationsByVersion)
        .as("auth module migration V8 must be discoverable from classpath*:db/migration/**/*.sql")
        .containsKey("8");
  }
}
