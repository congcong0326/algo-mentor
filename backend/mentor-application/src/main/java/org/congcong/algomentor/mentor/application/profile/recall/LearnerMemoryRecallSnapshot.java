package org.congcong.algomentor.mentor.application.profile.recall;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.RevisionStatus;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.Grade;

/** 单个 Practice Chat run 的只读 claim 快照；仅 registry 持有完整内容与不透明引用映射。 */
public record LearnerMemoryRecallSnapshot(
    String scopeRef,
    String documentRevision,
    String locale,
    List<Section> sections,
    List<Statement> directHits
) {

  public LearnerMemoryRecallSnapshot {
    scopeRef = requireText(scopeRef, "scope ref");
    documentRevision = requireText(documentRevision, "document revision");
    locale = LearnerMemoryDocumentRevision.normalizeLocale(locale);
    sections = sections == null ? List.of() : List.copyOf(sections);
    directHits = directHits == null ? List.of() : List.copyOf(directHits);
    if (sections.stream().anyMatch(Objects::isNull) || directHits.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Learner memory recall snapshot must not contain null items");
    }
  }

  public int claimCount() {
    return sections.stream().mapToInt(section -> section.statements().size()).sum();
  }

  public int currentProblemMatchCount() {
    return sections.stream().mapToInt(Section::currentProblemMatchCount).sum();
  }

  public record Section(
      String sectionRef,
      String catalogId,
      String title,
      List<Statement> statements,
      Instant latestUpdatedAt,
      int currentProblemMatchCount
  ) {

    public Section {
      sectionRef = requireText(sectionRef, "section ref");
      catalogId = requireText(catalogId, "catalog id");
      title = requireText(title, "section title");
      statements = statements == null ? List.of() : List.copyOf(statements);
      if (statements.stream().anyMatch(Objects::isNull) || currentProblemMatchCount < 0) {
        throw new IllegalArgumentException("Learner memory section is invalid");
      }
    }
  }

  public record Statement(
      String statementRef,
      LearnerMemoryClaimRevision claim,
      String sourceSummary,
      Grade grade,
      Instant updatedAt,
      boolean currentProblemMatch
  ) {

    public Statement {
      statementRef = requireText(statementRef, "statement ref");
      if (claim == null || claim.status() != RevisionStatus.ACTIVE || sourceSummary == null || sourceSummary.isBlank()
          || grade == null || updatedAt == null) {
        throw new IllegalArgumentException("Learner memory statement is invalid");
      }
      sourceSummary = sourceSummary.trim();
    }
  }

  /** registry 生成 opaque refs 前使用的受信 claim 投影输入。 */
  public record SectionInput(String catalogId, String title, Collection<StatementInput> statements) {

    public SectionInput {
      catalogId = requireText(catalogId, "catalog id");
      title = requireText(title, "section title");
      statements = statements == null ? List.of() : List.copyOf(statements);
      if (statements.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Learner memory section input must not contain null statements");
      }
    }
  }

  public record StatementInput(LearnerMemoryClaimRevision claim, String sourceSummary, boolean currentProblemMatch) {

    public StatementInput {
      if (claim == null || claim.status() != RevisionStatus.ACTIVE || sourceSummary == null || sourceSummary.isBlank()) {
        throw new IllegalArgumentException("Learner memory statement input is invalid");
      }
      sourceSummary = sourceSummary.trim();
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
