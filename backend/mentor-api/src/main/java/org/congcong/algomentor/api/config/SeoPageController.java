package org.congcong.algomentor.api.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 构建期生成的公开题目页 clean URL 映射。
 *
 * <p>页面本身是 classpath 静态制品；控制器只负责拒绝不存在的 slug，避免落入 SPA 首页。</p>
 */
@Controller
public class SeoPageController {

  private static final String PROBLEMS_INDEX = "/problems/index.html";
  private static final String PROBLEMS_RESOURCE_PREFIX = "classpath:/static/problems/";
  private static final String EN_PROBLEMS_RESOURCE_PREFIX = "classpath:/static/en/problems/";

  private final ResourceLoader resourceLoader;

  public SeoPageController(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @GetMapping("/problems")
  public String problemIndex(HttpServletResponse response) throws IOException {
    return forwardIfPresent(PROBLEMS_INDEX, response);
  }

  @GetMapping("/problems/{slug:[A-Za-z0-9][A-Za-z0-9_-]*}")
  public String chineseProblem(@PathVariable String slug, HttpServletResponse response) throws IOException {
    return forwardIfPresent("/problems/" + slug + "/index.html", response);
  }

  @GetMapping("/en/problems/{slug:[A-Za-z0-9][A-Za-z0-9_-]*}")
  public String englishProblem(@PathVariable String slug, HttpServletResponse response) throws IOException {
    return forwardIfPresent("/en/problems/" + slug + "/index.html", response);
  }

  private String forwardIfPresent(String path, HttpServletResponse response) throws IOException {
    String resourcePath = path.startsWith("/en/problems/")
        ? EN_PROBLEMS_RESOURCE_PREFIX + path.substring("/en/problems/".length())
        : PROBLEMS_RESOURCE_PREFIX + path.substring("/problems/".length());
    Resource resource = resourceLoader.getResource(resourcePath);
    if (!resource.exists() || !resource.isReadable()) {
      response.sendError(HttpStatus.NOT_FOUND.value());
      return null;
    }
    return "forward:" + path;
  }
}
