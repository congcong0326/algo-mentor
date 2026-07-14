package org.congcong.algomentor.auth.controller.admin.model;

import java.util.List;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailAddStatus;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailBatchResult;

public record BetaAllowedEmailBatchResponse(
    int addedCount,
    int existingCount,
    int invalidCount,
    List<Item> results
) {

  public static BetaAllowedEmailBatchResponse from(BetaAllowedEmailBatchResult result) {
    return new BetaAllowedEmailBatchResponse(
        result.addedCount(),
        result.existingCount(),
        result.invalidCount(),
        result.results().stream()
            .map(item -> new Item(item.email(), item.status(), item.allowedEmailId()))
            .toList());
  }

  public record Item(String email, BetaAllowedEmailAddStatus status, Long allowedEmailId) {
  }
}
