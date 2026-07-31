package org.congcong.algomentor.mentor.application.profile.tool;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;

/**
 * 单次记忆更新 run 的本地能力 scope。
 *
 * <p>scopeRef 为不可枚举随机能力令牌。注册表不支持按用户或 Review 回退查询，工具只能通过有效 lease
 * 关联的 scope 读取已授权范围。</p>
 */
public final class LearnerMemoryRunScopeRegistry {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
  private static final int REF_BYTES = 32;

  private final ConcurrentHashMap<String, ScopeState> scopes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, RecallScopeState> recallScopes = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom;
  private final Clock clock;
  private final Duration ttl;

  public LearnerMemoryRunScopeRegistry() {
    this(new SecureRandom(), Clock.systemUTC(), DEFAULT_TTL);
  }

  LearnerMemoryRunScopeRegistry(SecureRandom secureRandom, Clock clock, Duration ttl) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("Scope TTL must be positive");
    }
    this.ttl = ttl;
  }

  public ScopeLease openUpdateScope(long userId, Collection<CodeReviewVerification> reviewFacts) {
    return openUpdateScope(userId, reviewFacts, LearnerMemoryAgentToolContracts.MAX_TOOL_CALLS);
  }

  public ScopeLease openUpdateScope(long userId, Collection<CodeReviewVerification> reviewFacts, int toolBudget) {
    if (userId < 1) {
      throw new IllegalArgumentException("Scope user id must be positive");
    }
    if (toolBudget < 1 || toolBudget > LearnerMemoryAgentToolContracts.MAX_TOOL_CALLS) {
      throw new IllegalArgumentException("Scope tool budget is outside the allowed range");
    }
    Map<Long, CodeReviewVerification> reviewsById = new LinkedHashMap<>();
    if (reviewFacts != null) {
      for (CodeReviewVerification review : reviewFacts) {
        if (review == null || reviewsById.putIfAbsent(review.reviewId(), review) != null) {
          throw new IllegalArgumentException("Scope review facts must have unique non-null review ids");
        }
      }
    }
    if (reviewsById.isEmpty()) {
      throw new IllegalArgumentException("Scope requires at least one trusted review fact");
    }
    removeExpired();
    String scopeRef = nextScopeRef();
    Instant expiresAt = clock.instant().plus(ttl);
    ScopeState state = new ScopeState(userId, Map.copyOf(reviewsById), toolBudget, expiresAt);
    while (scopes.putIfAbsent(scopeRef, state) != null) {
      scopeRef = nextScopeRef();
    }
    return new ScopeLease(this, scopeRef, state);
  }

  /**
   * 为 Practice Chat 创建仅当前 run 可见的 claim snapshot。完整 claim 只保存在内存 capability 中，
   * request metadata 只能携带由该 lease 暴露的 scopeRef 与统计值。
   */
  public RecallScopeLease openRecallScope(
      long userId,
      String documentRevision,
      String locale,
      Collection<LearnerMemoryRecallSnapshot.SectionInput> sectionInputs,
      Collection<Long> directHitRevisionIds
  ) {
    if (userId < 1 || documentRevision == null || documentRevision.isBlank()) {
      throw new IllegalArgumentException("Recall scope input is invalid");
    }
    List<LearnerMemoryRecallSnapshot.SectionInput> inputs = sectionInputs == null
        ? List.of()
        : List.copyOf(sectionInputs);
    if (inputs.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Recall scope sections must not contain null");
    }
    removeExpired();
    RecallScopeState state;
    String scopeRef;
    do {
      scopeRef = nextScopeRef();
      state = recallScopeState(userId, scopeRef, documentRevision.trim(), locale, inputs, directHitRevisionIds);
    } while (recallScopes.putIfAbsent(scopeRef, state) != null);
    return new RecallScopeLease(this, scopeRef, state);
  }

  /** 为一项业务记忆工具预留当前 run 的一次调用和可见字符额度。 */
  public RecallScopeUse reserveRecallTool(String scopeRef) {
    return reserveRecall(scopeRef, RecallUseKind.BUSINESS_TOOL, LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
  }

  /** 为来自记忆工具结果的 {@code read_tool_result} 预留范围读取和可见字符额度。 */
  public RecallScopeUse reserveRecallToolResultRead(String scopeRef, int requestedMaxChars) {
    int requested = Math.max(1, Math.min(requestedMaxChars, LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS));
    return reserveRecall(scopeRef, RecallUseKind.RESULT_READ, requested);
  }

  /** request metadata only carries an opaque ref and a zero-based visible counter. */
  public Map<String, Object> initialRequestMetadata(ScopeLease lease) {
    if (lease == null) {
      return Map.of();
    }
    return Map.of(
        LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF, lease.scopeRef(),
        LearnerMemoryAgentToolContracts.METADATA_TOOL_CALL_COUNT, 0);
  }

  public ScopeUse reserveTrajectory(String scopeRef, String problemSlug) {
    return reserve(scopeRef, ScopeRequest.trajectory(problemSlug));
  }

  public ScopeUse reserveEvidence(String scopeRef, long reviewId) {
    return reserve(scopeRef, ScopeRequest.evidence(reviewId));
  }

  public ScopeUse reserveDiff(String scopeRef, long fromReviewId, long toReviewId) {
    return reserve(scopeRef, ScopeRequest.diff(fromReviewId, toReviewId));
  }

  int activeScopeCount() {
    removeExpired();
    return scopes.size();
  }

  int activeRecallScopeCount() {
    removeExpired();
    return recallScopes.size();
  }

  private ScopeUse reserve(String scopeRef, ScopeRequest request) {
    if (scopeRef == null || scopeRef.isBlank()) {
      return ScopeUse.unavailable();
    }
    ScopeState state = scopes.get(scopeRef);
    if (state == null || state.isExpired(clock.instant())) {
      scopes.remove(scopeRef, state);
      return ScopeUse.unavailable();
    }
    return state.reserve(request);
  }

  private void release(String scopeRef) {
    if (scopeRef != null) {
      scopes.remove(scopeRef);
    }
  }

  private void releaseRecall(String scopeRef) {
    if (scopeRef != null) {
      recallScopes.remove(scopeRef);
    }
  }

  private RecallScopeUse reserveRecall(String scopeRef, RecallUseKind kind, int requestedMaxChars) {
    if (scopeRef == null || scopeRef.isBlank()) {
      return RecallScopeUse.unavailable();
    }
    RecallScopeState state = recallScopes.get(scopeRef);
    if (state == null || state.isExpired(clock.instant())) {
      recallScopes.remove(scopeRef, state);
      return RecallScopeUse.unavailable();
    }
    return state.reserve(kind, requestedMaxChars);
  }

  private void removeExpired() {
    Instant now = clock.instant();
    scopes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    recallScopes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
  }

  private String nextScopeRef() {
    byte[] bytes = new byte[REF_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static final class ScopeLease implements AgentRunResource {

    private final LearnerMemoryRunScopeRegistry registry;
    private final String scopeRef;
    private final ScopeState state;
    private final AtomicBoolean released = new AtomicBoolean();

    private ScopeLease(LearnerMemoryRunScopeRegistry registry, String scopeRef, ScopeState state) {
      this.registry = registry;
      this.scopeRef = scopeRef;
      this.state = state;
    }

    public String scopeRef() {
      return scopeRef;
    }

    /** 仅供更新编排记录低敏工具次数；release 后不会重新注册或扩大读取范围。 */
    public int toolCallCount() {
      return state.toolCallCount();
    }

    @Override
    public void release() {
      if (released.compareAndSet(false, true)) {
        registry.release(scopeRef);
      }
    }
  }

  /** Practice Chat 的只读 memory capability；release 后不允许从 ref 重新读取快照。 */
  public static final class RecallScopeLease implements AgentRunResource {

    private final LearnerMemoryRunScopeRegistry registry;
    private final String scopeRef;
    private final RecallScopeState state;
    private final AtomicBoolean released = new AtomicBoolean();

    private RecallScopeLease(LearnerMemoryRunScopeRegistry registry, String scopeRef, RecallScopeState state) {
      this.registry = registry;
      this.scopeRef = scopeRef;
      this.state = state;
    }

    public String scopeRef() {
      return scopeRef;
    }

    public LearnerMemoryRecallSnapshot snapshot() {
      return state.snapshot;
    }

    @Override
    public void release() {
      if (released.compareAndSet(false, true)) {
        registry.releaseRecall(scopeRef);
      }
    }
  }

  public record ScopeUse(ScopeUseStatus status, ScopeSnapshot scope) {

    private static ScopeUse unavailable() {
      return new ScopeUse(ScopeUseStatus.SCOPE_UNAVAILABLE, null);
    }

    private static ScopeUse of(ScopeUseStatus status, ScopeState state) {
      return new ScopeUse(status, status == ScopeUseStatus.GRANTED ? state.snapshot() : null);
    }

    public boolean granted() {
      return status == ScopeUseStatus.GRANTED;
    }
  }

  public record ScopeSnapshot(long userId, Map<Long, CodeReviewVerification> reviewsById, int toolCallsUsed) {

    public ScopeSnapshot {
      reviewsById = Map.copyOf(reviewsById);
    }

    public Set<String> allowedProblemSlugs() {
      return reviewsById.values().stream().map(CodeReviewVerification::problemSlug).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
  }

  public enum ScopeUseStatus {
    GRANTED,
    SCOPE_UNAVAILABLE,
    FORBIDDEN,
    ALREADY_USED,
    BUDGET_EXHAUSTED
  }

  private enum RequestType {
    TRAJECTORY,
    EVIDENCE,
    DIFF
  }

  private record ScopeRequest(RequestType type, String problemSlug, long fromReviewId, long toReviewId) {

    private static ScopeRequest trajectory(String problemSlug) {
      return new ScopeRequest(RequestType.TRAJECTORY, problemSlug == null ? "" : problemSlug.trim(), 0, 0);
    }

    private static ScopeRequest evidence(long reviewId) {
      return new ScopeRequest(RequestType.EVIDENCE, "", reviewId, 0);
    }

    private static ScopeRequest diff(long fromReviewId, long toReviewId) {
      return new ScopeRequest(RequestType.DIFF, "", fromReviewId, toReviewId);
    }
  }

  private static final class ScopeState {

    private final long userId;
    private final Map<Long, CodeReviewVerification> reviewsById;
    private final Set<String> allowedProblemSlugs;
    private final int toolBudget;
    private final Instant expiresAt;
    private final Set<String> trajectorySlugs = new java.util.HashSet<>();
    private boolean diffUsed;
    private int toolCallsUsed;

    private ScopeState(
        long userId,
        Map<Long, CodeReviewVerification> reviewsById,
        int toolBudget,
        Instant expiresAt
    ) {
      this.userId = userId;
      this.reviewsById = reviewsById;
      this.allowedProblemSlugs = reviewsById.values().stream().map(CodeReviewVerification::problemSlug)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
      this.toolBudget = toolBudget;
      this.expiresAt = expiresAt;
    }

    private boolean isExpired(Instant now) {
      return !expiresAt.isAfter(now);
    }

    private synchronized ScopeUse reserve(ScopeRequest request) {
      ScopeUseStatus authorization = authorize(request);
      if (authorization != ScopeUseStatus.GRANTED) {
        return ScopeUse.of(authorization, this);
      }
      if (toolCallsUsed >= toolBudget) {
        return ScopeUse.of(ScopeUseStatus.BUDGET_EXHAUSTED, this);
      }
      switch (request.type()) {
        case TRAJECTORY -> trajectorySlugs.add(request.problemSlug());
        case DIFF -> diffUsed = true;
        case EVIDENCE -> {
          // Evidence lookups are limited only by the shared per-run budget.
        }
      }
      toolCallsUsed++;
      return ScopeUse.of(ScopeUseStatus.GRANTED, this);
    }

    private ScopeUseStatus authorize(ScopeRequest request) {
      return switch (request.type()) {
        case TRAJECTORY -> !allowedProblemSlugs.contains(request.problemSlug())
            ? ScopeUseStatus.FORBIDDEN
            : trajectorySlugs.contains(request.problemSlug()) ? ScopeUseStatus.ALREADY_USED : ScopeUseStatus.GRANTED;
        case EVIDENCE -> reviewsById.containsKey(request.fromReviewId())
            ? ScopeUseStatus.GRANTED : ScopeUseStatus.FORBIDDEN;
        case DIFF -> validDiff(request) ? (diffUsed ? ScopeUseStatus.ALREADY_USED : ScopeUseStatus.GRANTED)
            : ScopeUseStatus.FORBIDDEN;
      };
    }

    private boolean validDiff(ScopeRequest request) {
      CodeReviewVerification from = reviewsById.get(request.fromReviewId());
      CodeReviewVerification to = reviewsById.get(request.toReviewId());
      return from != null && to != null && from.problemSlug().equals(to.problemSlug())
          && from.versionNo() < to.versionNo();
    }

    private ScopeSnapshot snapshot() {
      return new ScopeSnapshot(userId, reviewsById, toolCallsUsed);
    }

    private synchronized int toolCallCount() {
      return toolCallsUsed;
    }
  }

  private RecallScopeState recallScopeState(
      long userId,
      String scopeRef,
      String documentRevision,
      String locale,
      List<LearnerMemoryRecallSnapshot.SectionInput> inputs,
      Collection<Long> directHitRevisionIds
  ) {
    Map<Long, LearnerMemoryRecallSnapshot.Statement> statementsByRevisionId = new LinkedHashMap<>();
    List<LearnerMemoryRecallSnapshot.Section> sections = new java.util.ArrayList<>();
    for (LearnerMemoryRecallSnapshot.SectionInput input : inputs) {
      List<LearnerMemoryRecallSnapshot.Statement> statements = new java.util.ArrayList<>();
      for (LearnerMemoryRecallSnapshot.StatementInput statementInput : input.statements()) {
        if (statementInput.claim().userId() != userId) {
          throw new IllegalArgumentException("Recall scope claim user does not match scope user");
        }
        LearnerMemoryRecallSnapshot.Statement statement = new LearnerMemoryRecallSnapshot.Statement(
            nextScopeRef(),
            statementInput.claim(),
            statementInput.sourceSummary(),
            statementInput.claim().evidenceGrade(),
            statementInput.claim().updatedAt(),
            statementInput.currentProblemMatch());
        if (statementsByRevisionId.putIfAbsent(statementInput.claim().id(), statement) != null) {
          throw new IllegalArgumentException("Recall scope claim revisions must be unique");
        }
        statements.add(statement);
      }
      Instant latestUpdatedAt = statements.stream().map(LearnerMemoryRecallSnapshot.Statement::updatedAt)
          .max(Instant::compareTo).orElse(null);
      int currentProblemMatchCount = (int) statements.stream()
          .filter(LearnerMemoryRecallSnapshot.Statement::currentProblemMatch).count();
      sections.add(new LearnerMemoryRecallSnapshot.Section(
          nextScopeRef(), input.catalogId(), input.title(), statements, latestUpdatedAt, currentProblemMatchCount));
    }
    List<Long> directIds = directHitRevisionIds == null ? List.of() : List.copyOf(directHitRevisionIds);
    List<LearnerMemoryRecallSnapshot.Statement> directHits = new java.util.ArrayList<>();
    for (Long revisionId : directIds) {
      LearnerMemoryRecallSnapshot.Statement statement = revisionId == null ? null : statementsByRevisionId.get(revisionId);
      if (statement == null || directHits.contains(statement)) {
        throw new IllegalArgumentException("Recall direct hit must reference a scoped claim revision");
      }
      directHits.add(statement);
    }
    LearnerMemoryRecallSnapshot snapshot = new LearnerMemoryRecallSnapshot(
        scopeRef, documentRevision, locale, sections, directHits);
    return new RecallScopeState(userId, snapshot, clock.instant().plus(ttl), this::nextScopeRef);
  }

  private static final class RecallScopeState {

    private final long userId;
    private final LearnerMemoryRecallSnapshot snapshot;
    private final Instant expiresAt;
    private final Supplier<String> refSupplier;
    private final Map<String, RecallCursor> cursors = new HashMap<>();
    private int businessToolCalls;
    private int resultReads;
    private int reservedVisibleChars;
    private int visibleChars;

    private RecallScopeState(
        long userId,
        LearnerMemoryRecallSnapshot snapshot,
        Instant expiresAt,
        Supplier<String> refSupplier
    ) {
      this.userId = userId;
      this.snapshot = snapshot;
      this.expiresAt = expiresAt;
      this.refSupplier = refSupplier;
    }

    private boolean isExpired(Instant now) {
      return !expiresAt.isAfter(now);
    }

    private synchronized RecallScopeUse reserve(RecallUseKind kind, int requestedMaxChars) {
      if (kind == RecallUseKind.BUSINESS_TOOL
          && businessToolCalls >= LearnerMemoryRecallToolContracts.MAX_BUSINESS_TOOL_CALLS) {
        return RecallScopeUse.budgetExhausted();
      }
      if (kind == RecallUseKind.RESULT_READ
          && resultReads >= LearnerMemoryRecallToolContracts.MAX_RESULT_READS) {
        return RecallScopeUse.budgetExhausted();
      }
      int remaining = LearnerMemoryRecallToolContracts.MAX_TOTAL_VISIBLE_CHARS
          - visibleChars
          - reservedVisibleChars;
      int allocation = Math.min(Math.min(requestedMaxChars, LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS), remaining);
      if (allocation < 1) {
        return RecallScopeUse.budgetExhausted();
      }
      if (kind == RecallUseKind.BUSINESS_TOOL) {
        businessToolCalls++;
      } else {
        resultReads++;
      }
      reservedVisibleChars += allocation;
      return RecallScopeUse.granted(this, userId, snapshot, kind, allocation);
    }

    private synchronized void complete(RecallUseKind kind, int allocation, int actualVisibleChars) {
      reservedVisibleChars = Math.max(0, reservedVisibleChars - allocation);
      int visible = Math.max(0, Math.min(actualVisibleChars, allocation));
      visibleChars = Math.min(LearnerMemoryRecallToolContracts.MAX_TOTAL_VISIBLE_CHARS, visibleChars + visible);
    }

    private synchronized String createCursor(String type, String subject, int offset) {
      String cursor;
      do {
        cursor = refSupplier.get();
      } while (cursors.containsKey(cursor));
      cursors.put(cursor, new RecallCursor(type, subject, offset));
      return cursor;
    }

    private synchronized OptionalInt resolveCursor(String cursor, String type, String subject) {
      RecallCursor value = cursor == null || cursor.isBlank() ? null : cursors.get(cursor);
      if (value == null || !value.type().equals(type) || !value.subject().equals(subject)) {
        return OptionalInt.empty();
      }
      return OptionalInt.of(value.offset());
    }
  }

  /** 当前 run 对一次记忆读取的不可重复完成票据。 */
  public static final class RecallScopeUse {

    private final RecallScopeState state;
    private final long userId;
    private final LearnerMemoryRecallSnapshot snapshot;
    private final RecallUseKind kind;
    private final int maxVisibleChars;
    private final RecallScopeUseStatus status;
    private final AtomicBoolean completed = new AtomicBoolean();

    private RecallScopeUse(
        RecallScopeState state,
        long userId,
        LearnerMemoryRecallSnapshot snapshot,
        RecallUseKind kind,
        int maxVisibleChars,
        RecallScopeUseStatus status
    ) {
      this.state = state;
      this.userId = userId;
      this.snapshot = snapshot;
      this.kind = kind;
      this.maxVisibleChars = maxVisibleChars;
      this.status = status;
    }

    private static RecallScopeUse granted(
        RecallScopeState state,
        long userId,
        LearnerMemoryRecallSnapshot snapshot,
        RecallUseKind kind,
        int maxVisibleChars
    ) {
      return new RecallScopeUse(state, userId, snapshot, kind, maxVisibleChars, RecallScopeUseStatus.GRANTED);
    }

    private static RecallScopeUse unavailable() {
      return new RecallScopeUse(null, 0, null, null, 0, RecallScopeUseStatus.SCOPE_UNAVAILABLE);
    }

    private static RecallScopeUse budgetExhausted() {
      return new RecallScopeUse(null, 0, null, null, 0, RecallScopeUseStatus.BUDGET_EXHAUSTED);
    }

    public boolean granted() {
      return status == RecallScopeUseStatus.GRANTED;
    }

    public RecallScopeUseStatus status() {
      return status;
    }

    public long userId() {
      return userId;
    }

    public LearnerMemoryRecallSnapshot snapshot() {
      return snapshot;
    }

    public int maxVisibleChars() {
      return maxVisibleChars;
    }

    public String createCursor(String type, String subject, int offset) {
      if (!granted()) {
        throw new IllegalStateException("Recall scope use is not granted");
      }
      return state.createCursor(type, subject, offset);
    }

    public OptionalInt resolveCursor(String cursor, String type, String subject) {
      return granted() ? state.resolveCursor(cursor, type, subject) : OptionalInt.empty();
    }

    public void complete(int visibleChars) {
      if (granted() && completed.compareAndSet(false, true)) {
        state.complete(kind, maxVisibleChars, visibleChars);
      }
    }
  }

  public enum RecallScopeUseStatus {
    GRANTED,
    SCOPE_UNAVAILABLE,
    BUDGET_EXHAUSTED
  }

  private enum RecallUseKind {
    BUSINESS_TOOL,
    RESULT_READ
  }

  private record RecallCursor(String type, String subject, int offset) {

    private RecallCursor {
      if (type == null || type.isBlank() || subject == null || subject.isBlank() || offset < 0) {
        throw new IllegalArgumentException("Recall cursor is invalid");
      }
    }
  }
}
