package org.congcong.algomentor.api.knowledge.importer;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import javax.sql.DataSource;
import org.congcong.algomentor.api.knowledge.model.KnowledgeSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 一个事务替换所有共享内容，用户复习状态和流水始终不在删除范围。 */
public final class KnowledgeContentImporter {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper json = new ObjectMapper();

  public KnowledgeContentImporter(DataSource dataSource) {
    jdbc = new JdbcTemplate(dataSource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  public Result replace(KnowledgeSource source) {
    if (source.nodes().isEmpty() || source.cards().isEmpty() && source.articles().isEmpty())
      throw new IllegalArgumentException("拒绝空快照");
    return transactions.execute(
        transaction -> {
          jdbc.execute("SET LOCAL lock_timeout = '15s'");
          jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", IMPORT_LOCK);
          var removed =
              new HashSet<>(jdbc.queryForList("SELECT slug FROM knowledge_card", String.class));
          source.cards().forEach(card -> removed.remove(card.slug()));
          jdbc.update("DELETE FROM knowledge_card_relation");
          jdbc.update("DELETE FROM knowledge_card");
          jdbc.update("DELETE FROM knowledge_article");
          while (jdbc.update(
                  "DELETE FROM knowledge_outline_node n WHERE NOT EXISTS (SELECT 1 FROM"
                      + " knowledge_outline_node c WHERE c.parent_id=n.id)")
              > 0) {}
          long root =
              jdbc.queryForObject(
                  "INSERT INTO knowledge_outline_node(node_kind,slug,title,status) VALUES(?,?,?,?)"
                      + " RETURNING id",
                  Long.class,
                  ROOT,
                  "root",
                  "知识库",
                  PUBLISHED);
          var ids = new HashMap<String, Long>();
          ids.put("", root);
          for (var node : source.nodes()) {
            Long parent = ids.get(node.parentPath());
            if (parent == null) throw new IllegalArgumentException("缺少父节点：" + node.path());
            long id =
                jdbc.queryForObject(
                    "INSERT INTO"
                        + " knowledge_outline_node(parent_id,node_kind,slug,title,sort_order,status)"
                        + " VALUES(?,?,?,?,?,?) RETURNING id",
                    Long.class,
                    parent,
                    NODE,
                    node.title(),
                    node.title(),
                    node.order(),
                    PUBLISHED);
            ids.put(node.path(), id);
          }
          for (var card : source.cards()) {
            jdbc.update(
                "INSERT INTO"
                    + " knowledge_card(outline_node_id,slug,question,answer_markdown,explanation_markdown,tags_json,sort_order,status)"
                    + " VALUES(?,?,?,?,?,?::jsonb,?,?)",
                ids.get(card.nodePath()),
                card.slug(),
                card.question(),
                card.answer(),
                card.explanation(),
                json(card.tags()),
                card.order(),
                card.status());
          }
          for (var article : source.articles()) {
            jdbc.update(
                "INSERT INTO"
                    + " knowledge_article(outline_node_id,title,body_markdown,sort_order,status,published_at)"
                    + " VALUES(?,?,?,?,?,NOW())",
                ids.get(article.nodePath()),
                article.title(),
                article.body(),
                article.order(),
                PUBLISHED);
          }
          for (var card : source.cards())
            for (var relation : card.relations().entrySet()) {
              int order = 0;
              for (var target : relation.getValue())
                jdbc.update(
                    "INSERT INTO"
                        + " knowledge_card_relation(source_slug,target_slug,relation_type,sort_order)"
                        + " VALUES(?,?,?,?)",
                    card.slug(),
                    target,
                    relation.getKey().key(),
                    order++);
            }
          return new Result(
              source.nodes().size(),
              source.cards().size(),
              source.articles().size(),
              removed.stream().sorted().toList());
        });
  }

  private String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("元数据无法序列化", e);
    }
  }

  public record Result(int nodes, int cards, int articles, List<String> removedSlugs) {}
}
