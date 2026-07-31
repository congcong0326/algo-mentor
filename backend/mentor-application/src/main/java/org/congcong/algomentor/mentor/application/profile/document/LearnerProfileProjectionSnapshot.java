package org.congcong.algomentor.mentor.application.profile.document;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;

/** 一次批量读取形成的只读投影输入。 */
public record LearnerProfileProjectionSnapshot(
    List<Claim> claims,
    Map<Long, List<LearnerProfileDocument.EvidenceItem>> evidenceByRevision
) {

  public LearnerProfileProjectionSnapshot {
    claims = claims == null ? List.of() : List.copyOf(claims);
    Map<Long, List<LearnerProfileDocument.EvidenceItem>> copy = new LinkedHashMap<>();
    if (evidenceByRevision != null) {
      evidenceByRevision.forEach((revisionId, evidence) -> copy.put(revisionId,
          evidence == null ? List.of() : List.copyOf(evidence)));
    }
    evidenceByRevision = Collections.unmodifiableMap(copy);
  }

  public record Claim(LearnerMemoryClaimRevision revision, String tagLabelEn, String tagLabelZh) {
    public Claim {
      if (revision == null) {
        throw new IllegalArgumentException("projection claim is required");
      }
    }
  }
}
