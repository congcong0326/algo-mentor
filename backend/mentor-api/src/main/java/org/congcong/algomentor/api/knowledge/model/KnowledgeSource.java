package org.congcong.algomentor.api.knowledge.model;

import java.util.List;
import java.util.Map;

/** 一次完整扫描的不可变内容快照，路径只用于本次重建大纲。 */
public record KnowledgeSource(List<Node> nodes, List<Card> cards, List<Article> articles) {
  public KnowledgeSource {
    nodes = List.copyOf(nodes);
    cards = List.copyOf(cards);
    articles = List.copyOf(articles);
  }

  public record Node(String path, String parentPath, String title, int order) {}

  public record Card(
      String nodePath,
      String slug,
      String question,
      String answer,
      String explanation,
      List<String> tags,
      String status,
      int order,
      Map<KnowledgeRelationType, List<String>> relations) {}

  public record Article(String nodePath, String title, String body, int order) {}
}
