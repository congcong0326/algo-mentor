package org.congcong.algomentor.mentor.application.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.support.TransactionTemplate;

/** 使用用户行锁执行短事务画像版本切换；模型调用必须发生在本服务外。 */
public class LearnerProfileUpdateService {

  private final LearnerProfileRepository repository;
  private final LearnerProfileContentPolicy contentPolicy;
  private final TransactionTemplate transactionTemplate;

  public LearnerProfileUpdateService(
      LearnerProfileRepository repository,
      LearnerProfileContentPolicy contentPolicy,
      TransactionTemplate transactionTemplate) {
    this.repository = repository;
    this.contentPolicy = contentPolicy;
    this.transactionTemplate = transactionTemplate;
  }

  public ProfileUpdateApplyResult apply(ProfileUpdateCommand command) {
    return applyBatch(List.of(command)).get(0);
  }

  public List<ProfileUpdateApplyResult> applyBatch(List<ProfileUpdateCommand> commands) {
    if (commands == null || commands.isEmpty()) {
      return List.of();
    }
    long userId = commands.get(0).identity().userId();
    if (commands.stream().anyMatch(command -> command.identity().userId() != userId)) {
      throw new IllegalArgumentException("Learner profile batch must contain one user");
    }
    if (commands.stream().map(ProfileUpdateCommand::identity).distinct().count() != commands.size()) {
      throw new IllegalArgumentException("Learner profile batch must not contain duplicate identities");
    }
    List<IndexedCommand> ordered = new ArrayList<>();
    for (int index = 0; index < commands.size(); index++) {
      ordered.add(new IndexedCommand(index, commands.get(index)));
    }
    ordered.sort(Comparator.comparing(indexed -> identitySortKey(indexed.command().identity())));
    List<ProfileUpdateApplyResult> orderedResults = transactionTemplate.execute(status -> applyLocked(userId, ordered));
    List<ProfileUpdateApplyResult> results = new ArrayList<>(java.util.Collections.nCopies(commands.size(), null));
    for (int index = 0; index < ordered.size(); index++) {
      results.set(ordered.get(index).originalIndex(), orderedResults.get(index));
    }
    return List.copyOf(results);
  }

  void suppress(LearnerProfileIdentity identity) {
    transactionTemplate.executeWithoutResult(status -> {
      repository.lockUser(identity.userId());
      repository.findCurrentForUpdate(identity).ifPresent(
          entry -> repository.markInactive(entry.id(), LearnerProfileEntryStatus.SUPPRESSED, Instant.now()));
    });
  }

  void deleteIdentity(LearnerProfileIdentity identity) {
    transactionTemplate.executeWithoutResult(status -> {
      repository.lockUser(identity.userId());
      repository.deleteIdentity(identity);
    });
  }

  private List<ProfileUpdateApplyResult> applyLocked(long userId, List<IndexedCommand> indexedCommands) {
    repository.lockUser(userId);
    List<LearnerProfileEntry> current = indexedCommands.stream()
        .map(indexed -> repository.findCurrentForUpdate(indexed.command().identity()).orElse(null)).toList();
    for (int index = 0; index < indexedCommands.size(); index++) {
      ProfileUpdateCommand command = indexedCommands.get(index).command();
      if (!LearnerProfileSnapshot.from(command.identity(), current.get(index)).snapshotToken()
          .equals(command.expectedSnapshotToken())) {
        return indexedCommands.stream().map(candidate -> {
          LearnerProfileEntry entry = repository.findCurrentForUpdate(candidate.command().identity()).orElse(null);
          LearnerProfileSnapshot snapshot = LearnerProfileSnapshot.from(candidate.command().identity(), entry);
          return new ProfileUpdateApplyResult(ProfileUpdateApplyStatus.STALE, snapshot.currentEntry(), snapshot.snapshotToken());
        }).toList();
      }
    }
    return java.util.stream.IntStream.range(0, indexedCommands.size()).mapToObj(index -> {
      ProfileUpdateCommand command = indexedCommands.get(index).command();
      LearnerProfileEntry existing = current.get(index);
      if (command.decision().action() == ProfileUpdateAction.NO_CHANGE) {
        LearnerProfileSnapshot snapshot = LearnerProfileSnapshot.from(command.identity(), existing);
        return new ProfileUpdateApplyResult(ProfileUpdateApplyStatus.NO_CHANGE, snapshot.currentEntry(), snapshot.snapshotToken());
      }
      Instant now = Instant.now();
      if (existing != null) {
        repository.markInactive(existing.id(), LearnerProfileEntryStatus.SUPERSEDED, now);
      }
      LearnerProfileEntry inserted = repository.insert(new LearnerProfileEntryDraft(
          command.identity(), existing == null ? 1 : existing.revisionNo() + 1, LearnerProfileEntryStatus.ACTIVE,
          contentPolicy.validateAndNormalize(command.decision().content()), existing == null ? null : existing.id(),
          command.originType(), command.modelProvider(), command.modelName(), command.promptVersion(), now, null));
      return new ProfileUpdateApplyResult(ProfileUpdateApplyStatus.APPLIED, java.util.Optional.of(inserted),
          LearnerProfileSnapshot.from(command.identity(), inserted).snapshotToken());
    }).toList();
  }

  private static String identitySortKey(LearnerProfileIdentity identity) {
    return identity.entryKind().name() + ':' + identity.dimension().name() + ':'
        + (identity.tagId() == null ? "" : identity.tagId());
  }

  private record IndexedCommand(int originalIndex, ProfileUpdateCommand command) {
  }
}
