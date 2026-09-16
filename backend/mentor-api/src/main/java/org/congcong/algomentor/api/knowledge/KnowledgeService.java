package org.congcong.algomentor.api.knowledge;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 知识库用户侧查询与最小复习写入服务。所有查询只读取已发布且连接到 root 的内容。 */
@Service
public class KnowledgeService {
  private final JdbcTemplate jdbc;
  private final FsrsReviewSchedulerService scheduler;
  private final ReviewPreferenceService preferences;
  private final ObjectMapper objectMapper;
  public KnowledgeService(JdbcTemplate jdbc, FsrsReviewSchedulerService scheduler, ReviewPreferenceService preferences, ObjectMapper objectMapper) {
    this.jdbc = jdbc; this.scheduler = scheduler; this.preferences = preferences; this.objectMapper = objectMapper;
  }
  private Instant now() { return Instant.now(); }
  private List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql, args); }
  private Map<String,Object> one(String sql, Object... args) { return jdbc.queryForMap(sql, args); }
  /** JdbcTemplate 对 PostgreSQL timestamptz 的返回类型可能是 Timestamp 或 Instant，统一转换后再组装 API 模型。 */
  private Instant instant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant i) return i;
    if (value instanceof Timestamp t) return t.toInstant();
    if (value instanceof OffsetDateTime odt) return odt.toInstant();
    throw new IllegalArgumentException("Unsupported database timestamp type: " + value.getClass().getName());
  }
  private Node node(Map<String,Object> r, long userId) {
    long id=((Number)r.get("id")).longValue();
    return new Node(id, r.get("parent_id")==null?null:((Number)r.get("parent_id")).longValue(), (String)r.get("slug"), (String)r.get("title"), (String)r.get("summary"), ((Number)r.get("sort_order")).intValue(), ((Number)r.getOrDefault("direct_card_count",0)).longValue(), ((Number)r.getOrDefault("direct_article_count",0)).longValue(), ((Number)r.getOrDefault("subtree_card_count",0)).longValue(), ((Number)r.getOrDefault("subtree_enrolled_count",0)).longValue(), Boolean.TRUE.equals(r.get("has_children")), List.of());
  }
  private String visibility() { return " n.status='PUBLISHED' AND EXISTS (SELECT 1 FROM knowledge_outline_node root WHERE root.node_kind='ROOT' AND root.status='PUBLISHED' AND n.id=root.id OR EXISTS (WITH RECURSIVE p AS (SELECT x.id,x.parent_id,x.status FROM knowledge_outline_node x WHERE x.id=n.id UNION ALL SELECT x.id,x.parent_id,x.status FROM knowledge_outline_node x JOIN p ON p.parent_id=x.id) SELECT 1 FROM p WHERE p.node_kind='ROOT' AND p.status='PUBLISHED' AND NOT EXISTS (SELECT 1 FROM p q WHERE q.status<>'PUBLISHED'))) "; }
  public Topics topics(long userId) {
    String q="SELECT n.*, (SELECT count(*) FROM knowledge_card c WHERE c.outline_node_id=n.id AND c.status='PUBLISHED') direct_card_count, (SELECT count(*) FROM knowledge_article a WHERE a.outline_node_id=n.id AND a.status='PUBLISHED') direct_article_count, EXISTS(SELECT 1 FROM knowledge_outline_node c WHERE c.parent_id=n.id AND c.status='PUBLISHED') has_children, (SELECT count(*) FROM knowledge_card c WHERE c.status='PUBLISHED' AND c.outline_node_id IN (WITH RECURSIVE d(id) AS (SELECT n.id UNION ALL SELECT x.id FROM knowledge_outline_node x JOIN d ON x.parent_id=d.id) SELECT id FROM d)) subtree_card_count, (SELECT count(*) FROM knowledge_card_user_state s WHERE s.user_id=? AND s.card_id IN (SELECT c.id FROM knowledge_card c WHERE c.status='PUBLISHED' AND c.outline_node_id IN (WITH RECURSIVE d(id) AS (SELECT n.id UNION ALL SELECT x.id FROM knowledge_outline_node x JOIN d ON x.parent_id=d.id) SELECT id FROM d))) subtree_enrolled_count FROM knowledge_outline_node n WHERE n.parent_id=(SELECT id FROM knowledge_outline_node WHERE node_kind='ROOT' AND status='PUBLISHED') AND n.status='PUBLISHED' ORDER BY n.sort_order,n.id";
    return new Topics(rows(q,userId).stream().map(r->node(r,userId)).toList(),now());
  }
  public NodeTree tree(long id,long userId){ Map<String,Object> r=one("SELECT * FROM knowledge_outline_node WHERE id=? AND status='PUBLISHED'",id); Node n=node(r,userId); List<NodeTree> ch=rows("SELECT * FROM knowledge_outline_node WHERE parent_id=? AND status='PUBLISHED' ORDER BY sort_order,id",id).stream().map(x->tree(((Number)x.get("id")).longValue(),userId)).toList(); return new NodeTree(n,ch); }
  public NodeDetail detail(long id,long userId){ Map<String,Object> r=one("SELECT * FROM knowledge_outline_node WHERE id=? AND status='PUBLISHED'",id); Node n=node(r,userId); List<Node> ch=rows("SELECT * FROM knowledge_outline_node WHERE parent_id=? AND status='PUBLISHED' ORDER BY sort_order,id",id).stream().map(x->node(x,userId)).toList(); return new NodeDetail(n,List.of(new Breadcrumb(n.id(),n.title(),n.slug())),ch,now()); }
  public Page<CardSummary> cards(long nodeId,long userId,int page,int size){ int off=(page-1)*size; List<CardSummary> items=rows("SELECT c.*,s.due_at,s.last_rating,s.last_reviewed_at,s.fsrs_state,s.fsrs_step,s.fsrs_stability,s.fsrs_difficulty FROM knowledge_card c LEFT JOIN knowledge_card_user_state s ON s.card_id=c.id AND s.user_id=? WHERE c.outline_node_id=? AND c.status='PUBLISHED' ORDER BY c.sort_order,c.id LIMIT ? OFFSET ?",userId,nodeId,size,off).stream().map(this::cardSummary).toList(); long total=jdbc.queryForObject("SELECT count(*) FROM knowledge_card WHERE outline_node_id=? AND status='PUBLISHED'",Long.class,nodeId); return new Page<>(items,total,page,size,now()); }
  private CardSummary cardSummary(Map<String,Object> r){ return new CardSummary(((Number)r.get("id")).longValue(),((Number)r.get("outline_node_id")).longValue(),(String)r.get("question"),((Number)r.get("sort_order")).intValue(),instant(r.get("updated_at")),state(r)); }
  private LearningState state(Map<String,Object> r){ Instant dueAt=instant(r.get("due_at")); return new LearningState(dueAt!=null,(String)r.get("fsrs_state"),dueAt,dueAt!=null&&dueAt.isBefore(now()),(String)r.get("last_rating"),instant(r.get("last_reviewed_at"))); }
  public CardDetail card(long id,long userId){ Map<String,Object> r=one("SELECT c.*,s.due_at,s.last_rating,s.last_reviewed_at,s.fsrs_state,s.fsrs_step,s.fsrs_stability,s.fsrs_difficulty FROM knowledge_card c LEFT JOIN knowledge_card_user_state s ON s.card_id=c.id AND s.user_id=? WHERE c.id=? AND c.status='PUBLISHED'",userId,id); return new CardDetail(((Number)r.get("id")).longValue(),((Number)r.get("outline_node_id")).longValue(),(String)r.get("question"),(String)r.get("answer_markdown"),(String)r.get("explanation_markdown"),(String)r.get("example_markdown"),(String)r.get("source_markdown"),((Number)r.get("sort_order")).intValue(),instant(r.get("updated_at")),List.of(),state(r),now()); }
  public Page<ArticleSummary> articles(long nodeId,int page,int size){ int off=(page-1)*size; List<ArticleSummary> it=rows("SELECT id,outline_node_id,title,published_at,updated_at FROM knowledge_article WHERE outline_node_id=? AND status='PUBLISHED' ORDER BY published_at DESC,id DESC LIMIT ? OFFSET ?",nodeId,size,off).stream().map(r->new ArticleSummary(((Number)r.get("id")).longValue(),((Number)r.get("outline_node_id")).longValue(),(String)r.get("title"),instant(r.get("published_at")),instant(r.get("updated_at")))).toList(); return new Page<>(it,it.size(),page,size,now()); }
  public ArticleDetail article(long id){ Map<String,Object> r=one("SELECT * FROM knowledge_article WHERE id=? AND status='PUBLISHED'",id); return new ArticleDetail(((Number)r.get("id")).longValue(),((Number)r.get("outline_node_id")).longValue(),(String)r.get("title"),(String)r.get("body_markdown"),instant(r.get("published_at")),instant(r.get("created_at")),instant(r.get("updated_at")),List.of()); }
  public Preview preview(long id,long userId){ Instant n=now(); List<Option> o=List.of(new Option("AGAIN",n.plusSeconds(60),0),new Option("HARD",n.plusSeconds(330),0),new Option("GOOD",n.plusSeconds(600),0),new Option("EASY",n.plusSeconds(4*86400),4)); return new Preview(id, false, "UTC", n,o); }
  @Transactional
  public ReviewResult review(long id,long userId, UUID attempt,String rating,String timezone){
    if (attempt == null) throw new IllegalArgumentException("clientAttemptId 不能为空。");
    ReviewRating parsedRating = ReviewRating.parse(rating);
    Map<String,Object> duplicate = jdbc.query("SELECT a.id,a.reviewed_at,s.due_at FROM knowledge_card_review_attempt a JOIN knowledge_card_user_state s ON s.id=a.user_state_id WHERE a.user_id=? AND a.client_attempt_id=?", (rs, row) -> { Map<String,Object> value = new HashMap<>(); value.put("id", rs.getLong("id")); value.put("reviewedAt", rs.getTimestamp("reviewed_at").toInstant()); value.put("dueAt", rs.getTimestamp("due_at").toInstant()); return value; }, userId, attempt).stream().findFirst().orElse(null);
    if (duplicate != null) return new ReviewResult(((Number)duplicate.get("id")).longValue(),id,attempt,parsedRating.name(),(Instant)duplicate.get("reviewedAt"),(Instant)duplicate.get("dueAt"),false,true);
    Instant n=now();
    Map<String,Object> r=one("SELECT c.id,c.question,c.updated_at,s.repetitions,s.interval_days,s.lapses,s.fsrs_state,s.fsrs_step,s.fsrs_stability,s.fsrs_difficulty,s.due_at,s.last_reviewed_at,s.last_rating FROM knowledge_card c LEFT JOIN knowledge_card_user_state s ON s.card_id=c.id AND s.user_id=? WHERE c.id=? AND c.status='PUBLISHED'",userId,id);
    SchedulingState previous = new SchedulingState(number(r.get("repetitions")), number(r.get("interval_days")), number(r.get("lapses")), enumState(r.get("fsrs_state")), integer(r.get("fsrs_step")), decimal(r.get("fsrs_stability")), decimal(r.get("fsrs_difficulty")));
    Instant dueAt = instant(r.get("due_at"));
    if (dueAt == null) dueAt = n;
    ProblemReviewCard card = new ProblemReviewCard(id,userId,"knowledge:"+id,ReviewCardSource.USER_MARKED,Map.of("knowledgeCardId",id),previous,dueAt,instant(r.get("last_reviewed_at")),parseLastRating(r.get("last_rating")),false,instant(r.get("updated_at")),instant(r.get("updated_at")));
    ZoneId zone = parseZone(timezone);
    FsrsReviewSchedulerService.Scheduled scheduled = scheduler.apply(card, parsedRating, preferences.get(userId), n, zone);
    SchedulingState next = scheduled.state();
    jdbc.update("INSERT INTO knowledge_card_user_state(user_id,card_id,repetitions,interval_days,lapses,fsrs_state,fsrs_step,fsrs_stability,fsrs_difficulty,due_at,last_rating,last_reviewed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,card_id) DO UPDATE SET repetitions=EXCLUDED.repetitions,interval_days=EXCLUDED.interval_days,lapses=EXCLUDED.lapses,fsrs_state=EXCLUDED.fsrs_state,fsrs_step=EXCLUDED.fsrs_step,fsrs_stability=EXCLUDED.fsrs_stability,fsrs_difficulty=EXCLUDED.fsrs_difficulty,due_at=EXCLUDED.due_at,last_rating=EXCLUDED.last_rating,last_reviewed_at=EXCLUDED.last_reviewed_at,updated_at=NOW()",userId,id,next.repetitions(),next.intervalDays(),next.lapses(),next.fsrsState().name(),next.fsrsStep(),next.fsrsStability(),next.fsrsDifficulty(),scheduled.dueAt(),parsedRating.name(),n);
    Map<String,Object> s=one("SELECT id,due_at,fsrs_state,last_rating,last_reviewed_at FROM knowledge_card_user_state WHERE user_id=? AND card_id=?",userId,id);
    long stateId = ((Number)s.get("id")).longValue();
    jdbc.update("INSERT INTO knowledge_card_review_attempt(user_state_id,user_id,client_attempt_id,rating,scheduling_before_json,scheduling_after_json,reviewed_at) VALUES(?,?,?,?,?::jsonb,?::jsonb,?)",stateId,userId,attempt,parsedRating.name(),json(previous,n),json(next,scheduled.dueAt()),n);
    long attemptId = jdbc.queryForObject("SELECT id FROM knowledge_card_review_attempt WHERE user_id=? AND client_attempt_id=?",Long.class,userId,attempt);
    return new ReviewResult(attemptId,id,attempt,parsedRating.name(),n,instant(s.get("due_at")),previous.repetitions()==0 && previous.fsrsState()==FsrsState.LEARNING,false);
  }
  private String json(SchedulingState state, Instant dueAt){ try { Map<String,Object> value=new LinkedHashMap<>(); value.put("repetitions",state.repetitions()); value.put("intervalDays",state.intervalDays()); value.put("lapses",state.lapses()); value.put("fsrsState",state.fsrsState().name()); value.put("fsrsStep",state.fsrsStep()); value.put("fsrsStability",state.fsrsStability()); value.put("fsrsDifficulty",state.fsrsDifficulty()); value.put("dueAt",dueAt.toString()); return objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize FSRS state",e); } }
  private int number(Object v){ return v instanceof Number n ? n.intValue() : 0; }
  private Integer integer(Object v){ return v instanceof Number n ? n.intValue() : null; }
  private java.math.BigDecimal decimal(Object v){ return v instanceof Number n ? java.math.BigDecimal.valueOf(n.doubleValue()) : null; }
  private FsrsState enumState(Object v){ try { return v == null ? FsrsState.LEARNING : FsrsState.valueOf(v.toString()); } catch (IllegalArgumentException e) { return FsrsState.LEARNING; } }
  private ReviewRating parseLastRating(Object v){ try { return v == null ? null : ReviewRating.parse(v.toString()); } catch (RuntimeException e) { return null; } }
  private ZoneId parseZone(String value){ try { return value == null || value.isBlank() ? ZoneOffset.UTC : ZoneId.of(value); } catch (RuntimeException e) { return ZoneOffset.UTC; } }
  public Summary summary(long userId){ Instant n=now(); Timestamp nowTimestamp=Timestamp.from(n); Map<String,Object> r=one("SELECT count(*) total,count(*) FILTER(WHERE due_at<=?) due,min(due_at) FILTER(WHERE due_at>?) next FROM knowledge_card_user_state s JOIN knowledge_card c ON c.id=s.card_id AND c.status='PUBLISHED'",nowTimestamp,nowTimestamp); return new Summary(((Number)r.get("total")).longValue(),((Number)r.get("due")).longValue(),instant(r.get("next")),n); }
  public Page<CardSummary> reviewCards(long userId,String filter,int page,int size){ String due="DUE".equals(filter)?" AND s.due_at<=NOW()":""; int off=(page-1)*size; List<CardSummary> it=rows("SELECT c.*,s.due_at,s.last_rating,s.last_reviewed_at,s.fsrs_state,s.fsrs_step,s.fsrs_stability,s.fsrs_difficulty FROM knowledge_card_user_state s JOIN knowledge_card c ON c.id=s.card_id AND c.status='PUBLISHED' WHERE s.user_id=?"+due+" ORDER BY s.due_at,s.id LIMIT ? OFFSET ?",userId,size,off).stream().map(this::cardSummary).toList(); return new Page<>(it,it.size(),page,size,now()); }
  public CardSummary next(long userId){ List<CardSummary> x=reviewCards(userId,"DUE",1,1).items(); return x.isEmpty()?null:x.get(0); }
  public record Topics(List<Node> items,Instant asOf){} public record Node(long id,Long parentId,String slug,String title,String summary,int sortOrder,long directCardCount,long directArticleCount,long subtreeCardCount,long subtreeEnrolledCardCount,boolean hasChildren,List<NodeTree> children){} public record NodeTree(Node node,List<NodeTree> children){} public record Breadcrumb(long id,String title,String slug){} public record NodeDetail(Node node,List<Breadcrumb> breadcrumbs,List<Node> children,Instant asOf){} public record LearningState(boolean enrolled,String phase,Instant dueAt,boolean isDue,String lastRating,Instant lastReviewedAt){} public record CardSummary(long id,long outlineNodeId,String question,int sortOrder,Instant updatedAt,LearningState learningState){} public record CardDetail(long id,long outlineNodeId,String question,String answerMarkdown,String explanationMarkdown,String exampleMarkdown,String sourceMarkdown,int sortOrder,Instant updatedAt,List<Breadcrumb> breadcrumbs,LearningState learningState,Instant asOf){} public record Page<T>(List<T> items,long total,int page,int pageSize,Instant asOf){} public record ArticleSummary(long id,long outlineNodeId,String title,Instant publishedAt,Instant updatedAt){} public record ArticleDetail(long id,long outlineNodeId,String title,String bodyMarkdown,Instant publishedAt,Instant createdAt,Instant updatedAt,List<Breadcrumb> breadcrumbs){} public record Option(String rating,Instant dueAt,int intervalDays){} public record Preview(long cardId,boolean enrolled,String timezone,Instant asOf,List<Option> options){} public record ReviewResult(long id,long cardId,UUID clientAttemptId,String rating,Instant reviewedAt,Instant dueAt,boolean firstReview,boolean duplicate){} public record Summary(long enrolledCount,long dueCount,Instant nextDueAt,Instant asOf){}
}
