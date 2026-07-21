package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

/** learner_profile_entry 的 MyBatis 行模型。 */
public record LearnerProfileEntryRow(
    long id,
    long userId,
    String entryKind,
    String dimension,
    Long tagId,
    int revisionNo,
    String status,
    String contentText,
    Long supersedesEntryId,
    String originType,
    String modelProvider,
    String modelName,
    String promptVersion,
    Instant validFrom,
    Instant validTo,
    Instant createdAt,
    Instant updatedAt
) {
}
