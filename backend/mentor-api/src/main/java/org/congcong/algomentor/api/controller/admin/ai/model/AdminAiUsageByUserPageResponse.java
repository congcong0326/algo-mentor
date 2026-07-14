package org.congcong.algomentor.api.controller.admin.ai.model;

import java.util.List;

public record AdminAiUsageByUserPageResponse(
    List<AdminAiUsageByUserResponse> items,
    long total,
    int page,
    int pageSize
) {
}
