package org.congcong.algomentor.auth.betaaccess.model;

import java.util.List;

public record BetaAllowedEmailBatchResult(
    int addedCount,
    int existingCount,
    int invalidCount,
    List<BetaAllowedEmailAddResult> results
) {

  public BetaAllowedEmailBatchResult {
    results = results == null ? List.of() : List.copyOf(results);
  }
}
