package org.congcong.algomentor.api.knowledge.importer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 按主题大纲的 children 顺序确定各层目录顺序，并核对清单覆盖的全部目录。 */
final class KnowledgeOutlineOrder {
  static final String FILE_NAME = "outline.json";
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

  private final Path spec;
  private final Map<Path, Integer> ranks = new LinkedHashMap<>();
  private final Set<Path> seen = new HashSet<>();

  private KnowledgeOutlineOrder(Path spec) {
    this.spec = spec;
  }

  static KnowledgeOutlineOrder read(Path spec) throws IOException {
    if (Files.isSymbolicLink(spec) || !Files.isRegularFile(spec))
      throw invalid(spec, "清单必须是普通文件");
    JsonNode document;
    try {
      document = JSON.readTree(Files.readString(spec));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw invalid(spec, "JSON 无法解析：" + e.getOriginalMessage());
    }
    if (document == null
        || !document.isObject()
        || document.size() != 2
        || !document.path("version").isInt()
        || document.path("version").intValue() != 1
        || !document.path("children").isArray()
        || document.path("children").isEmpty())
      throw invalid(spec, "清单只支持 version: 1 与非空 children 数组");
    var order = new KnowledgeOutlineOrder(spec);
    order.collect(spec.getParent(), document.get("children"));
    return order;
  }

  int rank(Path node) {
    Integer rank = ranks.get(node);
    if (rank == null) throw invalid(spec, "清单外节点：" + node);
    seen.add(node);
    return rank;
  }

  void verify() {
    for (var node : ranks.keySet())
      if (!seen.contains(node)) throw invalid(spec, "清单中的目录不存在：" + node);
  }

  private void collect(Path parent, JsonNode children) {
    if (!children.isArray()) throw invalid(spec, "children 必须是数组");
    var siblings = new HashSet<String>();
    for (int i = 0; i < children.size(); i++) {
      var node = children.get(i);
      if (!node.isObject()
          || node.size() < 1
          || node.size() > 2
          || !node.path("title").isTextual()
          || (node.has("children") && !node.get("children").isArray()))
        throw invalid(spec, "节点只支持 title 和 children 数组");
      var fields = node.fieldNames();
      while (fields.hasNext()) {
        var field = fields.next();
        if (!field.equals("title") && !field.equals("children"))
          throw invalid(spec, "未知节点字段：" + field);
      }
      String title = node.get("title").textValue();
      if (title.isBlank()
          || !title.equals(title.strip())
          || title.equals(".")
          || title.equals("..")
          || title.length() > 200
          || title.chars().anyMatch(c -> c == '/' || c == '\\' || c < 32 || c == 127))
        throw invalid(spec, "非法标题：" + title);
      if (!siblings.add(title)) throw invalid(spec, "同级标题重复：" + title);
      Path path = parent.resolve(title + ".node");
      ranks.put(path, i);
      if (node.has("children")) collect(path, node.get("children"));
    }
  }

  private static IllegalArgumentException invalid(Path spec, String message) {
    return new IllegalArgumentException(spec + "：" + message);
  }
}
