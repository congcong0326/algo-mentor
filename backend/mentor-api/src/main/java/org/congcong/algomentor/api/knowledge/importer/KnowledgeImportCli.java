package org.congcong.algomentor.api.knowledge.importer;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;

import java.nio.file.Path;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 无 Web/worker 的独立命令入口，复用应用制品和 Flyway 迁移。 */
public final class KnowledgeImportCli {
  private KnowledgeImportCli() {}

  public static boolean accepts(String[] args) {
    return args.length > 0 && Set.of(VALIDATE_COMMAND, IMPORT_COMMAND).contains(args[0]);
  }

  public static int run(String[] args) {
    try {
      if (args.length != 2)
        throw new IllegalArgumentException("用法：" + args[0] + " <knowledge-base 路径>");
      var source = new KnowledgeDirectoryReader().read(Path.of(args[1]));
      System.out.printf(
          "校验通过：%d 个大纲节点，%d 张卡片，%d 篇文章%n",
          source.nodes().size(), source.cards().size(), source.articles().size());
      if (VALIDATE_COMMAND.equals(args[0])) return 0;
      var db = new DriverManagerDataSource();
      db.setUrl(
          env(
              "SPRING_DATASOURCE_URL",
              "jdbc:postgresql://"
                  + env("POSTGRES_HOST", "localhost")
                  + ":"
                  + env("POSTGRES_PORT", "5432")
                  + "/"
                  + env("POSTGRES_DB", "algo_mentor")));
      db.setUsername(env("POSTGRES_USER", "algo_mentor"));
      db.setPassword(env("POSTGRES_PASSWORD", "algo_mentor_dev"));
      Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
      var result = new KnowledgeContentImporter(db).replace(source);
      System.out.printf(
          "导入完成：%d 个节点，%d 张卡片，%d 篇文章；移除卡片：%s；复习状态和流水保留%n",
          result.nodes(), result.cards(), result.articles(), result.removedSlugs());
      return 0;
    } catch (Exception e) {
      System.err.println("知识库导入失败：" + e.getMessage());
      return 1;
    }
  }

  private static String env(String key, String fallback) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? fallback : value;
  }
}
