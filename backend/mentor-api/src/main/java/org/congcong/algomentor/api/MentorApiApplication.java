package org.congcong.algomentor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("org.congcong.algomentor.api")
public class MentorApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(MentorApiApplication.class, args);
  }
}
