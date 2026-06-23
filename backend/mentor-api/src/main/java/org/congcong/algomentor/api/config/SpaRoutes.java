package org.congcong.algomentor.api.config;

/**
 * 前端 History API 路由和静态入口契约。
 */
public final class SpaRoutes {

  /**
   * React SPA 的入口页面。
   */
  public static final String INDEX_HTML = "index.html";

  /**
   * Spring MVC 转发到 SPA 入口的视图名。
   */
  public static final String INDEX_FORWARD = "forward:/" + INDEX_HTML;

  /**
   * 需要由后端部署态转发到 SPA 入口的前端页面路由。
   */
  public static final String[] FRONTEND_ROUTES = {
      "/login",
      "/learning-plans",
      "/problems",
      "/debug"
  };

  private SpaRoutes() {
  }
}
