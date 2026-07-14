package org.congcong.algomentor.ai.governance.adminquery;

import java.util.List;

/** 按用户维度分页的结果。 */
public record AiUsageByUserPage(
    List<AiUsageByUserRow> items,
    long total,
    int page,
    int pageSize
) {
}
