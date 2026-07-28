package org.congcong.algomentor.api.databasebackup.model;

import java.time.Instant;

public record DatabaseRestoreResponse(
    Instant restoredAt,
    int tableCount,
    long dumpSizeBytes,
    boolean loginRequired
) {
}
