package org.congcong.algomentor.mentor.application.review.note;

import java.util.List;
import org.congcong.algomentor.mentor.application.review.ReviewException;

public record ProblemSolutionOutlineV1(
    int schemaVersion,
    String coreIdea,
    List<ProblemDataStructureKey> dataStructures,
    List<String> customDataStructures,
    String dataStructureNotes,
    List<ProblemAlgorithmKey> algorithms,
    List<String> customAlgorithms,
    String algorithmNotes,
    ProblemComplexityValue timeComplexity,
    ProblemComplexityValue spaceComplexity,
    String edgeCases
) {
  public ProblemSolutionOutlineV1 {
    if (schemaVersion != 1) {
      throw new ReviewException("PROBLEM_NOTE_SCHEMA_INVALID", "题目笔记纲要版本不受支持。");
    }
    coreIdea = normalize(coreIdea);
    dataStructures = dataStructures == null ? List.of() : List.copyOf(dataStructures);
    customDataStructures = normalizeList(customDataStructures);
    dataStructureNotes = dataStructures.isEmpty() ? "" : normalize(dataStructureNotes);
    algorithms = algorithms == null ? List.of() : List.copyOf(algorithms);
    customAlgorithms = normalizeList(customAlgorithms);
    algorithmNotes = algorithms.isEmpty() ? "" : normalize(algorithmNotes);
    timeComplexity = timeComplexity == null ? ProblemComplexityValue.empty() : timeComplexity;
    spaceComplexity = spaceComplexity == null ? ProblemComplexityValue.empty() : spaceComplexity;
    edgeCases = normalize(edgeCases);
  }

  public static ProblemSolutionOutlineV1 empty() {
    return new ProblemSolutionOutlineV1(
        1,
        "",
        List.of(),
        List.of(),
        "",
        List.of(),
        List.of(),
        "",
        ProblemComplexityValue.empty(),
        ProblemComplexityValue.empty(),
        "");
  }

  public boolean hasContent() {
    return !coreIdea.isBlank()
        || !dataStructures.isEmpty()
        || !customDataStructures.isEmpty()
        || !dataStructureNotes.isBlank()
        || !algorithms.isEmpty()
        || !customAlgorithms.isEmpty()
        || !algorithmNotes.isBlank()
        || timeComplexity.key() != null
        || spaceComplexity.key() != null
        || !edgeCases.isBlank();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.strip();
  }

  private static List<String> normalizeList(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::strip)
        .distinct()
        .toList();
  }
}
