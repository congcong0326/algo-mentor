package org.congcong.algomentor.api.problem.model;

import java.util.List;

/** manifest 中用于精确替换空 hints、空关联和空模板的来源题范围。 */
public record ProblemMetadataSourceProblemRecord(String problemSlug, List<String> sourceSites) {

  public ProblemMetadataSourceProblemRecord {
    sourceSites = sourceSites == null ? List.of() : List.copyOf(sourceSites);
  }
}
