package org.congcong.algomentor.api;

import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.springframework.boot.SpringApplication;
import org.congcong.algomentor.api.knowledge.importer.KnowledgeImportCli;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UserInputLimitProperties.class)
public class MentorApiApplication {

  public static void main(String[] args) {
    if (KnowledgeImportCli.accepts(args)) {
      System.exit(KnowledgeImportCli.run(args));
      return;
    }
    SpringApplication.run(MentorApiApplication.class, args);
  }
}
