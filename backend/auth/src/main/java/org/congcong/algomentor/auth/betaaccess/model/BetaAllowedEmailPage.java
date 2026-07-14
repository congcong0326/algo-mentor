package org.congcong.algomentor.auth.betaaccess.model;

import java.util.List;

public record BetaAllowedEmailPage(
    BetaAccessSettings settings,
    List<BetaAllowedEmail> items,
    long total,
    int page,
    int pageSize
) {

  public BetaAllowedEmailPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
