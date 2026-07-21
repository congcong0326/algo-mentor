package org.congcong.algomentor.mentor.application.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class LearnerProfileUpdateServiceTest {

  @Test
  void createsThenReplacesAContinuousVersionChain() {
    FakeRepository repository = new FakeRepository();
    LearnerProfileUpdateService service = service(repository);
    LearnerProfileIdentity identity = declaredIdentity();

    ProfileUpdateApplyResult created = service.apply(command(identity, LearnerProfileSnapshot.ABSENT_TOKEN, "first"));
    ProfileUpdateApplyResult replaced = service.apply(command(identity, created.snapshotToken(), "second"));

    assertThat(created.status()).isEqualTo(ProfileUpdateApplyStatus.APPLIED);
    assertThat(replaced.status()).isEqualTo(ProfileUpdateApplyStatus.APPLIED);
    assertThat(repository.history(identity)).extracting(LearnerProfileEntry::revisionNo).containsExactly(2, 1);
    assertThat(repository.history(identity)).extracting(LearnerProfileEntry::status)
        .containsExactly(LearnerProfileEntryStatus.ACTIVE, LearnerProfileEntryStatus.SUPERSEDED);
    assertThat(repository.history(identity).get(0).supersedesEntryId()).isEqualTo(1L);
    assertThat(repository.lockCalls).isEqualTo(2);
  }

  @Test
  void noChangeWithMatchingSnapshotDoesNotWrite() {
    FakeRepository repository = new FakeRepository();
    LearnerProfileIdentity identity = declaredIdentity();
    LearnerProfileEntry existing = repository.seed(identity, "existing");
    int insertsBeforeApply = repository.insertCalls;

    ProfileUpdateApplyResult result = service(repository).apply(new ProfileUpdateCommand(
        identity,
        new ProfileUpdateDecision(ProfileUpdateAction.NO_CHANGE, null, "unchanged"),
        LearnerProfileSnapshot.from(identity, existing).snapshotToken(),
        LearnerProfileOriginType.USER_EXPLICIT,
        null, null, null));

    assertThat(result.status()).isEqualTo(ProfileUpdateApplyStatus.NO_CHANGE);
    assertThat(repository.insertCalls).isEqualTo(insertsBeforeApply);
    assertThat(repository.markInactiveCalls).isZero();
  }

  @Test
  void staleSnapshotIsRejectedForBothDecisionsWithoutWrites() {
    FakeRepository repository = new FakeRepository();
    LearnerProfileIdentity identity = declaredIdentity();
    repository.seed(identity, "current");
    int insertsBeforeApply = repository.insertCalls;
    LearnerProfileUpdateService service = service(repository);

    ProfileUpdateApplyResult replace = service.apply(command(identity, LearnerProfileSnapshot.ABSENT_TOKEN, "new"));
    ProfileUpdateApplyResult noChange = service.apply(new ProfileUpdateCommand(
        identity,
        new ProfileUpdateDecision(ProfileUpdateAction.NO_CHANGE, null, null),
        LearnerProfileSnapshot.ABSENT_TOKEN,
        LearnerProfileOriginType.USER_EXPLICIT,
        null, null, null));

    assertThat(replace.status()).isEqualTo(ProfileUpdateApplyStatus.STALE);
    assertThat(noChange.status()).isEqualTo(ProfileUpdateApplyStatus.STALE);
    assertThat(repository.insertCalls).isEqualTo(insertsBeforeApply);
    assertThat(repository.markInactiveCalls).isZero();
  }

  @Test
  void batchPreservesCallerOrderAndRejectsMixedUsersOrDuplicateIdentities() {
    FakeRepository repository = new FakeRepository();
    LearnerProfileUpdateService service = service(repository);
    LearnerProfileIdentity first = LearnerProfileIdentity.dimension(
        7, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.GOALS_AND_INTENTS);
    LearnerProfileIdentity second = declaredIdentity();

    List<ProfileUpdateApplyResult> results = service.applyBatch(List.of(
        command(first, LearnerProfileSnapshot.ABSENT_TOKEN, "goal"),
        command(second, LearnerProfileSnapshot.ABSENT_TOKEN, "background")));

    assertThat(results).extracting(result -> result.currentEntry().orElseThrow().identity())
        .containsExactly(first, second);
    assertThatThrownBy(() -> service.applyBatch(List.of(
        command(first, results.get(0).snapshotToken(), "goal2"),
        command(first, results.get(0).snapshotToken(), "goal3"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.applyBatch(List.of(
        command(first, results.get(0).snapshotToken(), "goal2"),
        command(LearnerProfileIdentity.dimension(
            8, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.LEARNER_BACKGROUND),
            LearnerProfileSnapshot.ABSENT_TOKEN, "other"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void queryServiceFiltersAndSortsRepositoryInputs() {
    FakeRepository repository = new FakeRepository();
    LearnerProfileIdentity background = declaredIdentity();
    LearnerProfileIdentity goals = LearnerProfileIdentity.dimension(
        7, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.GOALS_AND_INTENTS);
    repository.seed(background, "background");
    repository.seed(goals, "goals");

    List<LearnerProfileEntry> entries = new LearnerProfileQueryService(repository).findCurrentByDimensions(
        7, LearnerProfileEntryKind.DECLARED_FACT,
        List.of(LearnerProfileDimension.LEARNER_BACKGROUND, LearnerProfileDimension.GOALS_AND_INTENTS,
            LearnerProfileDimension.LEARNER_BACKGROUND));

    assertThat(entries).extracting(entry -> entry.identity().dimension())
        .containsExactly(LearnerProfileDimension.GOALS_AND_INTENTS, LearnerProfileDimension.LEARNER_BACKGROUND);
  }

  private LearnerProfileUpdateService service(FakeRepository repository) {
    return new LearnerProfileUpdateService(
        repository, new LearnerProfileContentPolicy(100), new TransactionTemplate(new NoOpTransactionManager()));
  }

  private ProfileUpdateCommand command(LearnerProfileIdentity identity, String token, String content) {
    return new ProfileUpdateCommand(
        identity,
        new ProfileUpdateDecision(ProfileUpdateAction.REPLACE, content, "test"),
        token,
        LearnerProfileOriginType.USER_EXPLICIT,
        "test-provider", "test-model", "v1");
  }

  private LearnerProfileIdentity declaredIdentity() {
    return LearnerProfileIdentity.dimension(
        7, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.LEARNER_BACKGROUND);
  }

  private static final class FakeRepository implements LearnerProfileRepository {
    private final Map<LearnerProfileIdentity, List<LearnerProfileEntry>> versions = new HashMap<>();
    private long nextId = 1;
    private int lockCalls;
    private int insertCalls;
    private int markInactiveCalls;

    @Override
    public Optional<LearnerProfileEntry> findCurrent(LearnerProfileIdentity identity) {
      return history(identity).stream().filter(entry -> entry.status() == LearnerProfileEntryStatus.ACTIVE).findFirst();
    }

    @Override
    public void lockUser(long userId) {
      lockCalls++;
    }

    @Override
    public Optional<LearnerProfileEntry> findCurrentForUpdate(LearnerProfileIdentity identity) {
      return findCurrent(identity);
    }

    @Override
    public List<LearnerProfileEntry> findHistory(LearnerProfileIdentity identity) {
      return history(identity);
    }

    @Override
    public List<LearnerProfileEntry> findCurrentByDimensions(
        long userId, LearnerProfileEntryKind entryKind, Collection<LearnerProfileDimension> dimensions) {
      return versions.values().stream().flatMap(List::stream)
          .filter(entry -> entry.identity().userId() == userId && entry.identity().entryKind() == entryKind)
          .filter(entry -> entry.status() == LearnerProfileEntryStatus.ACTIVE
              && dimensions.contains(entry.identity().dimension()))
          .sorted(Comparator.comparing(entry -> entry.identity().dimension().name())).toList();
    }

    @Override
    public List<LearnerProfileEntry> findCurrentByTagIds(long userId, Collection<Long> tagIds) {
      return List.of();
    }

    @Override
    public LearnerProfileEntry insert(LearnerProfileEntryDraft draft) {
      insertCalls++;
      LearnerProfileEntry entry = entry(
          nextId++, draft.identity(), draft.revisionNo(), draft.status(), draft.contentText(), draft.supersedesEntryId());
      List<LearnerProfileEntry> entries = new ArrayList<>(versions.getOrDefault(draft.identity(), List.of()));
      entries.add(0, entry);
      versions.put(draft.identity(), entries);
      return entry;
    }

    @Override
    public void markInactive(long entryId, LearnerProfileEntryStatus status, Instant validTo) {
      markInactiveCalls++;
      versions.replaceAll((identity, entries) -> entries.stream().map(entry -> entry.id() == entryId
          ? new LearnerProfileEntry(entry.id(), entry.identity(), entry.revisionNo(), status, entry.contentText(),
              entry.supersedesEntryId(), entry.originType(), entry.modelProvider(), entry.modelName(),
              entry.promptVersion(), entry.validFrom(), validTo, entry.createdAt(), Instant.now())
          : entry).toList());
    }

    @Override
    public void deleteIdentity(LearnerProfileIdentity identity) {
      versions.remove(identity);
    }

    LearnerProfileEntry seed(LearnerProfileIdentity identity, String content) {
      return insert(new LearnerProfileEntryDraft(
          identity, 1, LearnerProfileEntryStatus.ACTIVE, content, null,
          LearnerProfileOriginType.USER_EXPLICIT, null, null, null, Instant.now(), null));
    }

    List<LearnerProfileEntry> history(LearnerProfileIdentity identity) {
      return List.copyOf(versions.getOrDefault(identity, List.of()));
    }

    private LearnerProfileEntry entry(
        long id, LearnerProfileIdentity identity, int revision, LearnerProfileEntryStatus status, String content,
        Long supersedesEntryId) {
      Instant now = Instant.now();
      return new LearnerProfileEntry(
          id, identity, revision, status, content, supersedesEntryId, LearnerProfileOriginType.USER_EXPLICIT,
          null, null, null, now, status == LearnerProfileEntryStatus.ACTIVE ? null : now, now, now);
    }
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
    }
  }
}
