package org.congcong.algomentor.api.profile.model;

import java.time.Instant;

public record LearnerProfileEntryResponse(
    long id,
    String dimension,
    int revisionNo,
    String contentText,
    String originType,
    Instant updatedAt,
    LearnerProfileTagResponse tag
) {
}
