package org.congcong.algomentor.api.knowledge.service;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;
import static org.congcong.algomentor.api.knowledge.model.KnowledgeModels.*;
import static org.congcong.algomentor.api.knowledge.model.KnowledgeScheduleFields.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.congcong.algomentor.api.knowledge.repository.KnowledgeRepository;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** slug 是学习身份；全量内容重建不改变学习状态或幂等评价结果。 */
@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class KnowledgeService {
  private final KnowledgeRepository repository;
  private final FsrsReviewSchedulerService scheduler;
  private final ReviewPreferenceService preferences;
  private final ObjectMapper mapper;

  public KnowledgeService(
      KnowledgeRepository repository,
      FsrsReviewSchedulerService scheduler,
      ReviewPreferenceService preferences,
      ObjectMapper mapper) {
    this.repository = repository;
    this.scheduler = scheduler;
    this.preferences = preferences;
    this.mapper = mapper;
  }

  public Topics topics(long user) {
    var all = repository.nodes(user);
    long root =
        all.stream()
            .filter(r -> ROOT.equals(r.get("node_kind")))
            .mapToLong(r -> number(r, "id"))
            .findFirst()
            .orElse(-1);
    return new Topics(
        all.stream()
            .filter(r -> r.get("parent_id") != null && number(r, "parent_id") == root)
            .map(this::node)
            .toList(),
        Instant.now());
  }

  public NodeTree tree(long id, long user) {
    var all = repository.nodes(user);
    requireNode(all, id);
    return treeOf(all, id);
  }

  private NodeTree treeOf(List<Map<String, Object>> all, long id) {
    var current = all.stream().filter(r -> number(r, "id") == id).findFirst().orElseThrow();
    return new NodeTree(
        node(current),
        all.stream()
            .filter(r -> r.get("parent_id") != null && number(r, "parent_id") == id)
            .map(r -> treeOf(all, number(r, "id")))
            .toList());
  }

  public NodeDetail detail(long id, long user) {
    var all = repository.nodes(user);
    var current = requireNode(all, id);
    return new NodeDetail(
        node(current),
        breadcrumbs(all, id),
        all.stream()
            .filter(r -> r.get("parent_id") != null && number(r, "parent_id") == id)
            .map(this::node)
            .toList(),
        Instant.now());
  }

  private Map<String, Object> requireNode(List<Map<String, Object>> all, long id) {
    return all.stream()
        .filter(r -> number(r, "id") == id && !ROOT.equals(r.get("node_kind")))
        .findFirst()
        .orElseThrow(KnowledgeException::notFound);
  }

  private List<Breadcrumb> breadcrumbs(List<Map<String, Object>> all, long id) {
    var result = new ArrayList<Breadcrumb>();
    var byId = new HashMap<Long, Map<String, Object>>();
    all.forEach(r -> byId.put(number(r, "id"), r));
    var current = byId.get(id);
    while (current != null && !ROOT.equals(current.get("node_kind"))) {
      result.add(
          new Breadcrumb(number(current, "id"), string(current, "title"), string(current, "slug")));
      current = byId.get(number(current, "parent_id"));
    }
    Collections.reverse(result);
    return result;
  }

  public Page<CardSummary> cards(long node, long user, int page, int size) {
    pagination(page, size);
    requireNode(repository.nodes(user), node);
    return new Page<>(
        repository.cards(node, user, size, (page - 1) * size).stream()
            .map(this::cardSummary)
            .toList(),
        repository.cardCount(node),
        page,
        size,
        Instant.now());
  }

  public CardDetail card(String slug, long user) {
    validateSlug(slug);
    var r = repository.card(slug, user).orElseThrow(KnowledgeException::notFound);
    return new CardDetail(
        slug,
        number(r, "outline_node_id"),
        string(r, "question"),
        string(r, "answer_markdown"),
        string(r, "explanation_markdown"),
        tags(r),
        repository.relations(slug).stream()
            .map(
                x ->
                    new Relation(
                        string(x, "relation_type"), string(x, "slug"), string(x, "question")))
            .toList(),
        breadcrumbs(repository.nodes(user), number(r, "outline_node_id")),
        learning(r));
  }

  public Page<ArticleSummary> articles(long node, int page, int size) {
    pagination(page, size);
    requireNode(repository.nodes(0), node);
    return new Page<>(
        repository.articles(node, size, (page - 1) * size).stream()
            .map(
                r ->
                    new ArticleSummary(
                        number(r, "id"), number(r, "outline_node_id"), string(r, "title")))
            .toList(),
        repository.articleCount(node),
        page,
        size,
        Instant.now());
  }

  public ArticleDetail article(long id) {
    var r = repository.article(id).orElseThrow(KnowledgeException::notFound);
    return new ArticleDetail(
        id,
        number(r, "outline_node_id"),
        string(r, "title"),
        string(r, "body_markdown"),
        breadcrumbs(repository.nodes(0), number(r, "outline_node_id")));
  }

  public Preview preview(String slug, long user, String timezone) {
    validateSlug(slug);
    var zone = zone(timezone);
    var r = repository.card(slug, user).orElseThrow(KnowledgeException::notFound);
    var now = Instant.now();
    var scheduled = scheduledCard(r, user, now);
    var preference = preferences.get(user);
    var options =
        Arrays.stream(ReviewRating.values())
            .map(
                rating -> {
                  var next = scheduler.preview(scheduled, rating, preference, now, zone);
                  return new Option(rating.name(), next.dueAt(), next.intervalDays());
                })
            .toList();
    return new Preview(slug, r.get("state_id") != null, zone.getId(), now, options);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public ReviewResult review(String slug, long user, UUID client, String rating, String timezone) {
    validateSlug(slug);
    if (client == null) throw new IllegalArgumentException("clientAttemptId 不能为空");
    var parsed = ReviewRating.parse(rating);
    var zone = zone(timezone);
    repository.lockReview(user);
    var duplicate = repository.attempt(user, client);
    if (duplicate.isPresent()) {
      var row = duplicate.get();
      if (!slug.equals(row.get("card_slug")) || !parsed.name().equals(row.get("rating")))
        throw KnowledgeException.conflict();
      var after = readMap(string(row, "scheduling_after_json"));
      return new ReviewResult(
          number(row, "id"),
          slug,
          client,
          parsed.name(),
          instant(row.get("reviewed_at")),
          Instant.parse((String) after.get(DUE_AT)),
          Boolean.TRUE.equals(after.get(FIRST_REVIEW)),
          true);
    }
    var row = repository.card(slug, user).orElseThrow(KnowledgeException::notFound);
    var now = Instant.now();
    var card = scheduledCard(row, user, now);
    var next = scheduler.apply(card, parsed, preferences.get(user), now, zone);
    boolean first = row.get("state_id") == null;
    var after = snapshot(next.state(), next.dueAt());
    after.put(FIRST_REVIEW, first);
    long state = repository.saveState(user, slug, after, now, parsed.name(), next.dueAt());
    long id =
        repository.saveAttempt(
            state,
            user,
            client,
            parsed.name(),
            json(snapshot(card.scheduling(), card.dueAt())),
            json(after),
            now);
    return new ReviewResult(id, slug, client, parsed.name(), now, next.dueAt(), first, false);
  }

  public Summary summary(long user) {
    var r = repository.summary(user);
    return new Summary(
        number(r, "enrolled_count"),
        number(r, "due_count"),
        instant(r.get("next_due_at")),
        Instant.now());
  }

  public Page<CardSummary> reviewCards(long user, String filter, int page, int size) {
    pagination(page, size);
    if (!Set.of(FILTER_ALL, FILTER_DUE).contains(filter))
      throw new IllegalArgumentException("无效复习筛选");
    boolean due = FILTER_DUE.equals(filter);
    var summary = summary(user);
    return new Page<>(
        repository.reviewCards(user, due, size, (page - 1) * size).stream()
            .map(this::cardSummary)
            .toList(),
        due ? summary.dueCount() : summary.enrolledCount(),
        page,
        size,
        Instant.now());
  }

  public CardSummary next(long user) {
    return repository.reviewCards(user, true, 1, 0).stream()
        .map(this::cardSummary)
        .findFirst()
        .orElse(null);
  }

  private Node node(Map<String, Object> r) {
    return new Node(
        number(r, "id"),
        r.get("parent_id") == null ? null : number(r, "parent_id"),
        string(r, "slug"),
        string(r, "title"),
        string(r, "summary"),
        (int) number(r, "sort_order"),
        number(r, "direct_card_count"),
        number(r, "direct_article_count"),
        number(r, "subtree_card_count"),
        number(r, "subtree_enrolled_count"),
        Boolean.TRUE.equals(r.get("has_children")));
  }

  private CardSummary cardSummary(Map<String, Object> r) {
    return new CardSummary(
        string(r, "slug"),
        number(r, "outline_node_id"),
        string(r, "question"),
        (int) number(r, "sort_order"),
        tags(r),
        learning(r));
  }

  private LearningState learning(Map<String, Object> r) {
    var due = instant(r.get("due_at"));
    return new LearningState(
        r.get("state_id") != null,
        string(r, "fsrs_state"),
        due,
        due != null && !due.isAfter(Instant.now()),
        string(r, "last_rating"),
        instant(r.get("last_reviewed_at")));
  }

  private ProblemReviewCard scheduledCard(Map<String, Object> r, long user, Instant now) {
    var state =
        new SchedulingState(
            (int) number(r, REPETITIONS),
            (int) number(r, "interval_days"),
            (int) number(r, LAPSES),
            r.get("fsrs_state") == null
                ? FsrsState.LEARNING
                : FsrsState.valueOf(string(r, "fsrs_state")),
            r.get("fsrs_step") == null ? null : (int) number(r, "fsrs_step"),
            decimal(r.get("fsrs_stability")),
            decimal(r.get("fsrs_difficulty")));
    var due = instant(r.get("due_at"));
    return new ProblemReviewCard(
        r.get("state_id") == null ? 0 : number(r, "state_id"),
        user,
        string(r, "slug"),
        ReviewCardSource.USER_MARKED,
        Map.of(),
        state,
        due == null ? now : due,
        instant(r.get("last_reviewed_at")),
        r.get("last_rating") == null ? null : ReviewRating.parse(string(r, "last_rating")),
        false,
        now,
        now);
  }

  private Map<String, Object> snapshot(SchedulingState s, Instant due) {
    var m = new LinkedHashMap<String, Object>();
    m.put(REPETITIONS, s.repetitions());
    m.put(INTERVAL_DAYS, s.intervalDays());
    m.put(LAPSES, s.lapses());
    m.put(FSRS_STATE, s.fsrsState().name());
    m.put(FSRS_STEP, s.fsrsStep());
    m.put(FSRS_STABILITY, s.fsrsStability());
    m.put(FSRS_DIFFICULTY, s.fsrsDifficulty());
    m.put(DUE_AT, due.toString());
    return m;
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("序列化失败", e);
    }
  }

  private Map<String, Object> readMap(String value) {
    try {
      return mapper.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("流水数据无效", e);
    }
  }

  private List<String> tags(Map<String, Object> r) {
    try {
      return mapper.readValue(string(r, "tags_json"), new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("标签数据无效", e);
    }
  }

  private static long number(Map<String, Object> r, String key) {
    return r.get(key) instanceof Number n ? n.longValue() : 0;
  }

  private static String string(Map<String, Object> r, String key) {
    return r.get(key) == null ? null : r.get(key).toString();
  }

  private static BigDecimal decimal(Object value) {
    return value == null ? null : new BigDecimal(value.toString());
  }

  private static Instant instant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant i) return i;
    if (value instanceof Timestamp t) return t.toInstant();
    return ((OffsetDateTime) value).toInstant();
  }

  private static ZoneId zone(String value) {
    try {
      return value == null ? ZoneOffset.UTC : ZoneId.of(value);
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("无效时区");
    }
  }

  private static void validateSlug(String slug) {
    if (slug == null || slug.length() > SLUG_LIMIT || !slug.matches(SLUG_PATTERN))
      throw new IllegalArgumentException("无效卡片 slug");
  }

  private static void pagination(int page, int size) {
    if (page < 1 || size < 1 || size > 100 || (long) (page - 1) * size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("分页参数无效");
  }
}
