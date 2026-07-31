package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Dimension;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;

/** Bootstrap、记忆工具与文档投影共用的固定自然主题目录。 */
public final class LearnerMemorySectionCatalog {

  private static final List<SectionDefinition> SECTIONS = List.of(
      section("background-goals", "学习背景与目标",
          scope(Kind.DECLARED_FACT, Dimension.LEARNER_BACKGROUND),
          scope(Kind.DECLARED_FACT, Dimension.GOALS_AND_INTENTS)),
      section("learning-conditions", "学习方式与条件",
          scope(Kind.DECLARED_FACT, Dimension.TIME_AND_RESOURCE_CONSTRAINTS),
          scope(Kind.DECLARED_FACT, Dimension.LEARNING_AND_INTERACTION_PREFERENCES),
          scope(Kind.DECLARED_FACT, Dimension.SELF_ABILITY_ASSESSMENT)),
      section("problem-solving", "解题与实现",
          scope(Kind.GENERAL_OBSERVATION, Dimension.PROBLEM_SOLVING_APPROACH),
          scope(Kind.GENERAL_OBSERVATION, Dimension.IMPLEMENTATION_AND_ERROR_PATTERN)),
      section("review-growth", "复盘与成长",
          scope(Kind.GENERAL_OBSERVATION, Dimension.REVIEW_AND_GROWTH_PERFORMANCE),
          scope(Kind.GENERAL_OBSERVATION, Dimension.LEARNING_INTERACTION_AND_INDEPENDENCE)),
      section("knowledge-performance", "知识点表现",
          scope(Kind.TAG_ASSESSMENT, Dimension.TAG_MASTERY)));

  public List<SectionDefinition> sections() {
    return SECTIONS;
  }

  public Optional<SectionDefinition> find(LearnerMemoryClaimRevision claim) {
    if (claim == null) {
      return Optional.empty();
    }
    return SECTIONS.stream().filter(section -> section.matches(claim)).findFirst();
  }

  private static SectionDefinition section(String id, String title, Scope... scopes) {
    return new SectionDefinition(id, title, List.of(scopes));
  }

  private static Scope scope(Kind kind, Dimension dimension) {
    return new Scope(kind, dimension);
  }

  public record SectionDefinition(String id, String title, List<Scope> scopes) {

    public SectionDefinition {
      if (id == null || id.isBlank() || title == null || title.isBlank() || scopes == null || scopes.isEmpty()) {
        throw new IllegalArgumentException("Learner memory section definition is incomplete");
      }
      scopes = List.copyOf(scopes);
    }

    public boolean matches(LearnerMemoryClaimRevision claim) {
      return claim != null && scopes.stream().anyMatch(scope -> scope.matches(claim));
    }
  }

  public record Scope(Kind kind, Dimension dimension) {

    public Scope {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(dimension, "dimension");
    }

    private boolean matches(LearnerMemoryClaimRevision claim) {
      return claim.scope().kind() == kind && claim.scope().dimension() == dimension;
    }
  }
}
