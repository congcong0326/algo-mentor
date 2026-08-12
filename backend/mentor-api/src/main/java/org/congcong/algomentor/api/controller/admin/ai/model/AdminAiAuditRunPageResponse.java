package org.congcong.algomentor.api.controller.admin.ai.model;

import java.util.List;

/** 审计 run 分页响应。 */
public record AdminAiAuditRunPageResponse(
    List<AdminAiAuditRunResponse> items,
    long total,
    int page,
    int pageSize,
    AdminAiAuditRunStatisticsResponse statistics
) {
}
