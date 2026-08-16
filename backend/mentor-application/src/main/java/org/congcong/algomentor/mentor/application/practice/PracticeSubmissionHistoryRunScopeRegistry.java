package org.congcong.algomentor.mentor.application.practice;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;

/**
 * Practice Chat 历史正式提交 Tool 的 run-local capability registry。
 *
 * <p>所有裸 slug、Review ID、cursor keyset 都只保存在本进程内存；模型只得到不可枚举的 ref。scope
 * 释放或过期后不可重新打开，也不能退回到任意数据键查询。</p>
 */
public final class PracticeSubmissionHistoryRunScopeRegistry {

  public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
  private static final int REF_BYTES = 32;

  private final ConcurrentHashMap<String, ScopeState> scopes = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom;
  private final Clock clock;
  private final Duration ttl;

  public PracticeSubmissionHistoryRunScopeRegistry() {
    this(new SecureRandom(), Clock.systemUTC(), DEFAULT_TTL);
  }

  PracticeSubmissionHistoryRunScopeRegistry(SecureRandom secureRandom, Clock clock, Duration ttl) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("Practice submission history scope TTL must be positive");
    }
    this.ttl = ttl;
  }

  public ScopeLease openScope(long userId, String locale, Collection<PracticeSubmissionHistoryScopeInput> inputs) {
    if (userId < 1) {
      throw new IllegalArgumentException("Practice submission history scope user id must be positive");
    }
    Map<String, ScopedProblem> problemsByRef = new LinkedHashMap<>();
    if (inputs != null) {
      for (PracticeSubmissionHistoryScopeInput input : inputs) {
        if (input == null || problemsByRef.putIfAbsent(input.problemRef(), new ScopedProblem(
            input.problemRef(), input.problemSlug(), input.title(), input.tags())) != null) {
          throw new IllegalArgumentException("Practice submission history scope problem refs must be unique");
        }
      }
    }
    if (problemsByRef.isEmpty()) {
      throw new IllegalArgumentException("Practice submission history scope requires prompt-visible problems");
    }
    removeExpired();
    String scopeRef;
    ScopeState state;
    do {
      scopeRef = nextRef("ph_");
      state = new ScopeState(userId, normalizeLocale(locale), Map.copyOf(problemsByRef), clock.instant().plus(ttl));
    } while (scopes.putIfAbsent(scopeRef, state) != null);
    return new ScopeLease(this, scopeRef);
  }

  public ScopeUse reserveOverview(String scopeRef, String problemRef) {
    return reserve(scopeRef, ScopeRequest.overview(problemRef));
  }

  public ScopeUse reserveList(String scopeRef, String problemRef, String cursor) {
    return reserve(scopeRef, ScopeRequest.list(problemRef, cursor));
  }

  public ScopeUse reserveDetail(String scopeRef, String submissionRef) {
    return reserve(scopeRef, ScopeRequest.detail(submissionRef));
  }

  public DetailVisibleUse reserveDetailResultRead(String scopeRef, int requestedMaxChars) {
    ScopeState state = state(scopeRef);
    if (state == null) {
      return new DetailVisibleUse(DetailVisibleUseStatus.UNAVAILABLE, null, 0);
    }
    return state.reserveDetailRangeRead(Math.max(1, requestedMaxChars));
  }

  int activeScopeCount() {
    removeExpired();
    return scopes.size();
  }

  private ScopeUse reserve(String scopeRef, ScopeRequest request) {
    ScopeState state = state(scopeRef);
    return state == null ? new ScopeUse(ScopeUseStatus.UNAVAILABLE, null, null, null, null) : state.reserve(request);
  }

  private ScopeState state(String scopeRef) {
    if (scopeRef == null || scopeRef.isBlank()) {
      return null;
    }
    ScopeState state = scopes.get(scopeRef);
    if (state == null || state.isExpired(clock.instant())) {
      scopes.remove(scopeRef, state);
      return null;
    }
    return state;
  }

  private void release(String scopeRef) {
    if (scopeRef != null) {
      scopes.remove(scopeRef);
    }
  }

  private void removeExpired() {
    Instant now = clock.instant();
    scopes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
  }

  private String nextRef(String prefix) {
    byte[] bytes = new byte[REF_BYTES];
    secureRandom.nextBytes(bytes);
    return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String normalizeLocale(String locale) {
    return locale == null || locale.isBlank() ? "" : locale.trim();
  }

  public final class ScopeLease implements AgentRunResource {

    private final String scopeRef;
    private final AtomicBoolean released = new AtomicBoolean();

    private ScopeLease(PracticeSubmissionHistoryRunScopeRegistry registry, String scopeRef) {
      this.scopeRef = scopeRef;
    }

    public String scopeRef() {
      return scopeRef;
    }

    @Override
    public void release() {
      if (released.compareAndSet(false, true)) {
        PracticeSubmissionHistoryRunScopeRegistry.this.release(scopeRef);
      }
    }
  }

  /** 一次 Tool 授权包含服务端读取键；该对象绝不直接写入 Tool JSON。 */
  public final class ScopeUse {

    private final ScopeUseStatus status;
    private final ScopeState state;
    private final ScopedProblem problem;
    private final SubmissionTarget submission;
    private final Cursor cursor;

    private ScopeUse(
        ScopeUseStatus status,
        ScopeState state,
        ScopedProblem problem,
        SubmissionTarget submission,
        Cursor cursor
    ) {
      this.status = status;
      this.state = state;
      this.problem = problem;
      this.submission = submission;
      this.cursor = cursor;
    }

    public boolean granted() {
      return status == ScopeUseStatus.GRANTED;
    }

    public ScopeUseStatus status() {
      return status;
    }

    public long userId() {
      return requireGranted().userId;
    }

    public String locale() {
      return requireGranted().locale;
    }

    public ScopedProblem problem() {
      requireGranted();
      return problem;
    }

    public SubmissionTarget submission() {
      requireGranted();
      return submission;
    }

    public Instant afterCreatedAt() {
      return cursor == null ? null : cursor.createdAt();
    }

    public Long afterReviewId() {
      return cursor == null ? null : cursor.reviewId();
    }

    public String issueSubmissionRef(long reviewId) {
      return requireGranted().issueSubmissionRef(problem, reviewId);
    }

    public String issueNextCursor(Instant createdAt, long reviewId) {
      return requireGranted().issueCursor(problem.problemRef(), createdAt, reviewId);
    }

    /** 记录 detail 首次进入模型上下文的实际可见字符数。 */
    public boolean recordInitialDetailVisibleChars(int visibleChars) {
      return requireGranted().recordInitialDetailVisibleChars(Math.max(0, visibleChars));
    }

    private ScopeState requireGranted() {
      if (!granted()) {
        throw new IllegalStateException("Practice submission history scope use is not granted");
      }
      return state;
    }
  }

  /** Tool result range read 的不可重复完成票据。 */
  public final class DetailVisibleUse {

    private final DetailVisibleUseStatus status;
    private final ScopeState state;
    private final int maxVisibleChars;
    private final AtomicBoolean completed = new AtomicBoolean();

    private DetailVisibleUse(DetailVisibleUseStatus status, ScopeState state, int maxVisibleChars) {
      this.status = status;
      this.state = state;
      this.maxVisibleChars = maxVisibleChars;
    }

    public boolean granted() {
      return status == DetailVisibleUseStatus.GRANTED;
    }

    public DetailVisibleUseStatus status() {
      return status;
    }

    public int maxVisibleChars() {
      return maxVisibleChars;
    }

    public void complete(int visibleChars) {
      if (granted() && completed.compareAndSet(false, true)) {
        state.completeDetailRangeRead(maxVisibleChars, visibleChars);
      }
    }
  }

  public enum ScopeUseStatus {
    GRANTED,
    UNAVAILABLE,
    BUDGET_EXHAUSTED
  }

  public enum DetailVisibleUseStatus {
    GRANTED,
    UNAVAILABLE,
    BUDGET_EXHAUSTED
  }

  /** 仅供 application 内 Tool 使用的 scope 中题目展示事实与读取键。 */
  public record ScopedProblem(String problemRef, String problemSlug, String title, List<String> tags) {

    public ScopedProblem {
      if (problemRef == null || problemRef.isBlank() || problemSlug == null || problemSlug.isBlank()
          || title == null || title.isBlank()) {
        throw new IllegalArgumentException("Scoped practice submission problem is invalid");
      }
      problemRef = problemRef.trim();
      problemSlug = problemSlug.trim();
      title = title.trim();
      tags = tags == null ? List.of() : List.copyOf(tags);
    }
  }

  /** 仅供 application 内 Tool 使用的已签发正式提交读取键。 */
  public record SubmissionTarget(String problemSlug, long reviewId) {

    public SubmissionTarget {
      if (problemSlug == null || problemSlug.isBlank() || reviewId < 1) {
        throw new IllegalArgumentException("Scoped practice submission target is invalid");
      }
      problemSlug = problemSlug.trim();
    }
  }

  private enum RequestType {
    OVERVIEW,
    LIST,
    DETAIL
  }

  private record ScopeRequest(RequestType type, String problemRef, String submissionRef, String cursor) {

    private static ScopeRequest overview(String problemRef) {
      return new ScopeRequest(RequestType.OVERVIEW, normalize(problemRef), "", "");
    }

    private static ScopeRequest list(String problemRef, String cursor) {
      return new ScopeRequest(RequestType.LIST, normalize(problemRef), "", normalize(cursor));
    }

    private static ScopeRequest detail(String submissionRef) {
      return new ScopeRequest(RequestType.DETAIL, "", normalize(submissionRef), "");
    }

    private static String normalize(String value) {
      return value == null ? "" : value.trim();
    }
  }

  private record Cursor(String problemRef, Instant createdAt, long reviewId) {

    private Cursor {
      if (problemRef == null || problemRef.isBlank() || createdAt == null || reviewId < 1) {
        throw new IllegalArgumentException("Practice submission history cursor is invalid");
      }
    }
  }

  private final class ScopeState {

    private final long userId;
    private final String locale;
    private final Map<String, ScopedProblem> problemsByRef;
    private final Instant expiresAt;
    private final Map<String, SubmissionTarget> submissionsByRef = new HashMap<>();
    private final Map<Long, String> refsByReviewId = new HashMap<>();
    private final Map<String, Cursor> cursors = new HashMap<>();
    private final java.util.Set<String> overviewReads = new java.util.HashSet<>();
    private int overviewAndListCalls;
    private int detailCalls;
    private int detailRangeReads;
    private int detailVisibleChars;
    private int reservedDetailVisibleChars;

    private ScopeState(long userId, String locale, Map<String, ScopedProblem> problemsByRef, Instant expiresAt) {
      this.userId = userId;
      this.locale = locale;
      this.problemsByRef = problemsByRef;
      this.expiresAt = expiresAt;
    }

    private boolean isExpired(Instant now) {
      return !expiresAt.isAfter(now);
    }

    private synchronized ScopeUse reserve(ScopeRequest request) {
      if (request.type() == RequestType.DETAIL) {
        SubmissionTarget target = submissionsByRef.get(request.submissionRef());
        if (target == null) {
          return new ScopeUse(ScopeUseStatus.UNAVAILABLE, this, null, null, null);
        }
        if (detailCalls >= PracticeSubmissionHistoryToolContracts.MAX_DETAIL_CALLS) {
          return new ScopeUse(ScopeUseStatus.BUDGET_EXHAUSTED, this, null, null, null);
        }
        detailCalls++;
        return new ScopeUse(ScopeUseStatus.GRANTED, this, problemForSlug(target.problemSlug()), target, null);
      }

      ScopedProblem problem = problemsByRef.get(request.problemRef());
      if (problem == null) {
        return new ScopeUse(ScopeUseStatus.UNAVAILABLE, this, null, null, null);
      }
      Cursor cursor = null;
      if (request.type() == RequestType.LIST && !request.cursor().isBlank()) {
        cursor = cursors.get(request.cursor());
        if (cursor == null || !problem.problemRef().equals(cursor.problemRef())) {
          return new ScopeUse(ScopeUseStatus.UNAVAILABLE, this, null, null, null);
        }
      }
      if (request.type() == RequestType.OVERVIEW && overviewReads.contains(problem.problemRef())) {
        return new ScopeUse(ScopeUseStatus.BUDGET_EXHAUSTED, this, null, null, null);
      }
      if (overviewAndListCalls >= PracticeSubmissionHistoryToolContracts.MAX_OVERVIEW_AND_LIST_CALLS) {
        return new ScopeUse(ScopeUseStatus.BUDGET_EXHAUSTED, this, null, null, null);
      }
      overviewAndListCalls++;
      if (request.type() == RequestType.OVERVIEW) {
        overviewReads.add(problem.problemRef());
      }
      return new ScopeUse(ScopeUseStatus.GRANTED, this, problem, null, cursor);
    }

    private ScopedProblem problemForSlug(String problemSlug) {
      return problemsByRef.values().stream()
          .filter(problem -> problem.problemSlug().equals(problemSlug))
          .findFirst()
          .orElse(null);
    }

    private synchronized String issueSubmissionRef(ScopedProblem problem, long reviewId) {
      if (problem == null || reviewId < 1) {
        throw new IllegalArgumentException("Practice submission history submission ref input is invalid");
      }
      String existing = refsByReviewId.get(reviewId);
      if (existing != null) {
        return existing;
      }
      String ref;
      do {
        ref = nextRef("ps_");
      } while (submissionsByRef.containsKey(ref));
      submissionsByRef.put(ref, new SubmissionTarget(problem.problemSlug(), reviewId));
      refsByReviewId.put(reviewId, ref);
      return ref;
    }

    private synchronized String issueCursor(String problemRef, Instant createdAt, long reviewId) {
      String ref;
      do {
        ref = nextRef("pc_");
      } while (cursors.containsKey(ref));
      cursors.put(ref, new Cursor(problemRef, createdAt, reviewId));
      return ref;
    }

    private synchronized boolean recordInitialDetailVisibleChars(int visibleChars) {
      int safeVisible = Math.max(0, visibleChars);
      if (safeVisible > PracticeSubmissionHistoryToolContracts.MAX_DETAIL_VISIBLE_CHARS - detailVisibleChars
          - reservedDetailVisibleChars) {
        return false;
      }
      detailVisibleChars += safeVisible;
      return true;
    }

    private synchronized DetailVisibleUse reserveDetailRangeRead(int requestedMaxChars) {
      if (detailRangeReads >= PracticeSubmissionHistoryToolContracts.MAX_DETAIL_RESULT_READS) {
        return new DetailVisibleUse(DetailVisibleUseStatus.BUDGET_EXHAUSTED, null, 0);
      }
      int remaining = PracticeSubmissionHistoryToolContracts.MAX_DETAIL_VISIBLE_CHARS
          - detailVisibleChars - reservedDetailVisibleChars;
      int allocation = Math.min(Math.max(1, requestedMaxChars), remaining);
      if (allocation < 1) {
        return new DetailVisibleUse(DetailVisibleUseStatus.BUDGET_EXHAUSTED, null, 0);
      }
      detailRangeReads++;
      reservedDetailVisibleChars += allocation;
      return new DetailVisibleUse(DetailVisibleUseStatus.GRANTED, this, allocation);
    }

    private synchronized void completeDetailRangeRead(int allocation, int actualVisibleChars) {
      reservedDetailVisibleChars = Math.max(0, reservedDetailVisibleChars - allocation);
      int visible = Math.max(0, Math.min(allocation, actualVisibleChars));
      detailVisibleChars = Math.min(
          PracticeSubmissionHistoryToolContracts.MAX_DETAIL_VISIBLE_CHARS,
          detailVisibleChars + visible);
    }
  }
}
