package org.congcong.algomentor.api.knowledge.repository;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;
import static org.congcong.algomentor.api.knowledge.model.KnowledgeScheduleFields.*;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 内容读取和复习状态存储；所有用户事实按 user_id 与 card_slug 隔离。 */
@Repository
public class KnowledgeRepository {
  private final JdbcTemplate jdbc;

  public KnowledgeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String VISIBLE =
      """
      WITH RECURSIVE visible AS (
        SELECT id,parent_id FROM knowledge_outline_node WHERE node_kind='ROOT' AND status='PUBLISHED'
        UNION ALL SELECT n.id,n.parent_id FROM knowledge_outline_node n JOIN visible p ON n.parent_id=p.id WHERE n.status='PUBLISHED'
      )
      """;
  private static final String STATE_COLUMNS =
      """
      ,s.id state_id,s.due_at,s.last_rating,s.last_reviewed_at,s.repetitions,s.interval_days,s.lapses,
       s.fsrs_state,s.fsrs_step,s.fsrs_stability,s.fsrs_difficulty
      """;
  private static final String CARD_FROM =
      """
       FROM knowledge_card c JOIN visible v ON c.outline_node_id=v.id
       LEFT JOIN knowledge_card_user_state s ON s.card_slug=c.slug AND s.user_id=?
       WHERE c.status='PUBLISHED'
      """;

  public List<Map<String, Object>> nodes(long user) {
    return jdbc.queryForList(
        VISIBLE
            + """
        , descendants AS (
          SELECT id ancestor,id FROM visible
          UNION ALL SELECT d.ancestor,v.id FROM descendants d JOIN visible v ON v.parent_id=d.id
        )
        SELECT n.*,
          (SELECT count(*) FROM knowledge_card c WHERE c.outline_node_id=n.id AND c.status='PUBLISHED') direct_card_count,
          (SELECT count(*) FROM knowledge_article a WHERE a.outline_node_id=n.id AND a.status='PUBLISHED') direct_article_count,
          (SELECT count(*) FROM knowledge_card c JOIN descendants d ON d.id=c.outline_node_id WHERE d.ancestor=n.id AND c.status='PUBLISHED') subtree_card_count,
          (SELECT count(*) FROM knowledge_card c JOIN descendants d ON d.id=c.outline_node_id
            JOIN knowledge_card_user_state s ON s.card_slug=c.slug AND s.user_id=? WHERE d.ancestor=n.id AND c.status='PUBLISHED') subtree_enrolled_count,
          EXISTS(SELECT 1 FROM visible child WHERE child.parent_id=n.id) has_children
        FROM knowledge_outline_node n JOIN visible v ON v.id=n.id ORDER BY n.sort_order,n.id
        """,
        user);
  }

  public List<Map<String, Object>> cards(long node, long user, int size, int offset) {
    return jdbc.queryForList(
        VISIBLE
            + "SELECT c.*"
            + STATE_COLUMNS
            + CARD_FROM
            + " AND c.outline_node_id=? ORDER BY c.sort_order,c.question,c.slug LIMIT ? OFFSET ?",
        user,
        node,
        size,
        offset);
  }

  public long cardCount(long node) {
    return jdbc.queryForObject(
        VISIBLE
            + "SELECT count(*) FROM knowledge_card c JOIN visible v ON v.id=c.outline_node_id WHERE"
            + " c.outline_node_id=? AND c.status='PUBLISHED'",
        Long.class,
        node);
  }

  public Optional<Map<String, Object>> card(String slug, long user) {
    return jdbc
        .queryForList(
            VISIBLE + "SELECT c.*" + STATE_COLUMNS + CARD_FROM + " AND c.slug=?", user, slug)
        .stream()
        .findFirst();
  }

  public List<Map<String, Object>> relations(String slug) {
    return jdbc.queryForList(
        VISIBLE
            + """
        SELECT r.relation_type,c.slug,c.question FROM knowledge_card_relation r
        JOIN knowledge_card c ON c.slug=r.target_slug JOIN visible v ON v.id=c.outline_node_id
        WHERE r.source_slug=? AND c.status='PUBLISHED' ORDER BY r.relation_type,r.sort_order
        """,
        slug);
  }

  public List<Map<String, Object>> articles(long node, int size, int offset) {
    return jdbc.queryForList(
        VISIBLE
            + "SELECT a.* FROM knowledge_article a JOIN visible v ON v.id=a.outline_node_id WHERE"
            + " a.outline_node_id=? AND a.status='PUBLISHED' ORDER BY a.sort_order,a.title LIMIT ?"
            + " OFFSET ?",
        node,
        size,
        offset);
  }

  public long articleCount(long node) {
    return jdbc.queryForObject(
        VISIBLE
            + "SELECT count(*) FROM knowledge_article a JOIN visible v ON v.id=a.outline_node_id"
            + " WHERE a.outline_node_id=? AND a.status='PUBLISHED'",
        Long.class,
        node);
  }

  public Optional<Map<String, Object>> article(long id) {
    return jdbc
        .queryForList(
            VISIBLE
                + "SELECT a.* FROM knowledge_article a JOIN visible v ON v.id=a.outline_node_id"
                + " WHERE a.id=? AND a.status='PUBLISHED'",
            id)
        .stream()
        .findFirst();
  }

  public List<Map<String, Object>> reviewCards(long user, boolean due, int size, int offset) {
    return jdbc.queryForList(
        VISIBLE
            + "SELECT c.*"
            + STATE_COLUMNS
            + CARD_FROM
            + " AND s.id IS NOT NULL"
            + (due ? " AND s.due_at<=NOW()" : "")
            + " ORDER BY s.due_at,c.slug LIMIT ? OFFSET ?",
        user,
        size,
        offset);
  }

  public Map<String, Object> summary(long user) {
    return jdbc.queryForMap(
        VISIBLE
            + """
        SELECT count(*) enrolled_count,count(*) FILTER(WHERE s.due_at<=NOW()) due_count,
          min(s.due_at) FILTER(WHERE s.due_at>NOW()) next_due_at
        FROM knowledge_card_user_state s JOIN knowledge_card c ON c.slug=s.card_slug
        JOIN visible v ON v.id=c.outline_node_id WHERE s.user_id=? AND c.status='PUBLISHED'
        """,
        user);
  }

  public void lockReview(long user) {
    jdbc.execute("SET LOCAL lock_timeout = '15s'");
    jdbc.queryForList("SELECT pg_advisory_xact_lock_shared(?)", IMPORT_LOCK);
    // 同一用户的首次建状态与幂等请求串行；不同用户可以并行评价。
    jdbc.queryForList("SELECT pg_advisory_xact_lock(76, hashtext(?))", Long.toString(user));
  }

  public Optional<Map<String, Object>> attempt(long user, UUID id) {
    return jdbc
        .queryForList(
            """
        SELECT a.*,s.card_slug FROM knowledge_card_review_attempt a
        JOIN knowledge_card_user_state s ON s.id=a.user_state_id WHERE a.user_id=? AND a.client_attempt_id=?
        """,
            user,
            id)
        .stream()
        .findFirst();
  }

  public long saveState(
      long user,
      String slug,
      Map<String, Object> state,
      Instant reviewedAt,
      String rating,
      Instant dueAt) {
    return jdbc.queryForObject(
        """
        INSERT INTO knowledge_card_user_state(user_id,card_slug,repetitions,interval_days,lapses,fsrs_state,fsrs_step,fsrs_stability,fsrs_difficulty,due_at,last_rating,last_reviewed_at)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,card_slug) DO UPDATE SET
        repetitions=EXCLUDED.repetitions,interval_days=EXCLUDED.interval_days,lapses=EXCLUDED.lapses,
        fsrs_state=EXCLUDED.fsrs_state,fsrs_step=EXCLUDED.fsrs_step,fsrs_stability=EXCLUDED.fsrs_stability,fsrs_difficulty=EXCLUDED.fsrs_difficulty,
        due_at=EXCLUDED.due_at,last_rating=EXCLUDED.last_rating,last_reviewed_at=EXCLUDED.last_reviewed_at,updated_at=NOW() RETURNING id
        """,
        Long.class,
        user,
        slug,
        state.get(REPETITIONS),
        state.get(INTERVAL_DAYS),
        state.get(LAPSES),
        state.get(FSRS_STATE),
        state.get(FSRS_STEP),
        state.get(FSRS_STABILITY),
        state.get(FSRS_DIFFICULTY),
        java.sql.Timestamp.from(dueAt),
        rating,
        java.sql.Timestamp.from(reviewedAt));
  }

  public long saveAttempt(
      long state,
      long user,
      UUID client,
      String rating,
      String before,
      String after,
      Instant reviewedAt) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " knowledge_card_review_attempt(user_state_id,user_id,client_attempt_id,rating,scheduling_before_json,scheduling_after_json,reviewed_at)"
            + " VALUES(?,?,?,?,?::jsonb,?::jsonb,?) RETURNING id",
        Long.class,
        state,
        user,
        client,
        rating,
        before,
        after,
        java.sql.Timestamp.from(reviewedAt));
  }
}
