package org.congcong.algomentor.api.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UnifiedAgentFoundationArchitectureTest {

  @Test
  void productionModulesDoNotDependOnRemovedAgentEntrypoints() throws IOException {
    Path root = repositoryRoot();

    assertSourcesDoNotContain(
        root.resolve("backend/mentor-application/src/main/java"),
        List.of("AgentLoopRunner", "AgentRunner", "AiCompletionGateway"));
    assertSourcesDoNotContain(
        root.resolve("backend/mentor-api/src/main/java/org/congcong/algomentor/api/controller"),
        List.of("AiRunAdmissionService", "AiRunLifecycleService"));
    assertSourcesDoNotContain(
        root.resolve("backend/mentor-api/src/main/java/org/congcong/algomentor/api/learningplan/service"),
        List.of("AiRunAdmissionService", "AiRunLifecycleService"));
    assertSourcesDoNotContain(
        root.resolve("backend/agent-runtime/src/main/java"),
        List.of("AiCompletionGateway"));
  }

  @Test
  void coreAndApplicationBoundariesRemainIndependentFromBusinessRuntimeImplementations() throws IOException {
    Path root = repositoryRoot();

    assertSourcesDoNotContain(
        root.resolve("backend/agent-core/src/main/java"),
        List.of("AiBusinessScenario", "org.springframework"));
    assertSourcesDoNotContain(
        root.resolve("backend/mentor-application/src/main/java"),
        List.of("org.congcong.algomentor.agent.runtime."));
    assertThat(Files.readString(root.resolve("backend/mentor-application/pom.xml")))
        .doesNotContain("<artifactId>agent-runtime</artifactId>");
  }

  @Test
  void genericObserversDoNotEncodeBusinessScenarioMetadata() throws IOException {
    Path root = repositoryRoot();

    assertSourcesDoNotContain(
        root.resolve("backend/agent-persistence-postgres/src/main/java/org/congcong/algomentor/agent/persistence/postgres/observer/PersistentAgentRunObserver.java"),
        List.of("Practice", "PRACTICE", "LearningPlan", "LEARNING_PLAN", "Profile", "PROFILE"));
    assertSourcesDoNotContain(
        root.resolve("backend/ops-observability/src/main/java/org/congcong/algomentor/ops/observability/AgentOpsObserver.java"),
        List.of("Practice", "PRACTICE", "LearningPlan", "LEARNING_PLAN", "Profile", "PROFILE"));
  }

  private static void assertSourcesDoNotContain(Path sourcePath, List<String> forbiddenTokens) throws IOException {
    List<String> violations;
    if (Files.isDirectory(sourcePath)) {
      try (Stream<Path> paths = Files.walk(sourcePath)) {
        violations = paths
            .filter(path -> path.toString().endsWith(".java"))
            .flatMap(path -> violations(path, forbiddenTokens).stream())
            .toList();
      }
    } else {
      violations = violations(sourcePath, forbiddenTokens);
    }
    assertThat(violations).isEmpty();
  }

  private static List<String> violations(Path source, List<String> forbiddenTokens) {
    try {
      String content = Files.readString(source);
      return forbiddenTokens.stream()
          .filter(content::contains)
          .map(token -> source + ": " + token)
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read production source: " + source, exception);
    }
  }

  private static Path repositoryRoot() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null && !Files.isRegularFile(current.resolve("backend/pom.xml"))) {
      current = current.getParent();
    }
    if (current == null) {
      throw new IllegalStateException("Cannot locate algo-mentor repository root");
    }
    return current;
  }
}
