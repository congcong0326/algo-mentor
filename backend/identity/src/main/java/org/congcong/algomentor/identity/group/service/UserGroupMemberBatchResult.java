package org.congcong.algomentor.identity.group.service;

import java.util.List;

public record UserGroupMemberBatchResult(
    int addedCount,
    int updatedCount,
    int failedCount,
    List<UserGroupMemberAddResult> results
) {

  public UserGroupMemberBatchResult {
    results = results == null ? List.of() : List.copyOf(results);
  }

  public static UserGroupMemberBatchResult from(List<UserGroupMemberAddResult> results) {
    int added = 0;
    int updated = 0;
    int failed = 0;
    for (UserGroupMemberAddResult result : results) {
      if (result.status() == UserGroupMemberAddStatus.ADDED) {
        added++;
      } else if (result.status() == UserGroupMemberAddStatus.UPDATED) {
        updated++;
      } else {
        failed++;
      }
    }
    return new UserGroupMemberBatchResult(added, updated, failed, results);
  }
}
