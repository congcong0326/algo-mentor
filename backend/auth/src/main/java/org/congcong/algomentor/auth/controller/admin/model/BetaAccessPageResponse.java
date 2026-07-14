package org.congcong.algomentor.auth.controller.admin.model;

import java.util.List;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailPage;

public record BetaAccessPageResponse(
    BetaAccessSettingsResponse settings,
    List<BetaAllowedEmailResponse> items,
    long total,
    int page,
    int pageSize
) {

  public static BetaAccessPageResponse from(BetaAllowedEmailPage page) {
    return new BetaAccessPageResponse(
        BetaAccessSettingsResponse.from(page.settings()),
        page.items().stream().map(BetaAllowedEmailResponse::from).toList(),
        page.total(),
        page.page(),
        page.pageSize());
  }
}
