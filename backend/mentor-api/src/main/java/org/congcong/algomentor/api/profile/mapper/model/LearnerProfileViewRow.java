package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

/** 当前用户学习记忆展示查询行，不包含模型和 Prompt 等内部字段。 */
public record LearnerProfileViewRow(
    long id,
    String entryKind,
    String dimension,
    Long tagId,
    String tagValue,
    String tagLabelEn,
    String tagLabelZh,
    int revisionNo,
    String contentText,
    String originType,
    Instant updatedAt
) {
}
