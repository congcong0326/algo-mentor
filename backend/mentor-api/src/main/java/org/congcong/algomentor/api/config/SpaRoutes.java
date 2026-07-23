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
      "/me",
      "/learning-plans",
      "/admin/problems",
      "/admin/users",
      "/admin/user-groups",
      "/admin/debug",
      "/debug"
  };

  /**
   * 前端嵌套路由模式，由 Spring MVC 转发到 SPA 入口。
   */
  public static final String[] FRONTEND_ROUTE_PATTERNS = {
      "/learning-plans/{planId:[0-9]+}",
      "/learning-plans/{planId:[0-9]+}/phases/{phaseIndex:[0-9]+}/problems/{problemSlug}/chat",
      "/admin/user-groups/{groupId:[0-9]+}"
  };

  /**
   * 无扩展名前端 History API 路由兜底，避免新增页面 URL 后刷新浏览器落到后端 404/500。
   */
  public static final String FRONTEND_FALLBACK_TOP_LEVEL_PATTERN = "/{path:[^\\.]*}";
  public static final String FRONTEND_FALLBACK_NESTED_PATTERN = "/**/{path:[^\\.]*}";
  public static final String[] FRONTEND_FALLBACK_PATTERNS = {
      FRONTEND_FALLBACK_TOP_LEVEL_PATTERN,
      FRONTEND_FALLBACK_NESTED_PATTERN
  };

  /**
   * 由后端或静态资源处理器拥有的路径前缀，SPA fallback 不应接管。
   */
  public static final String[] BACKEND_RESERVED_PATH_PREFIXES = {
      "/api",
      "/assets",
      "/actuator",
      "/oauth2",
      "/login/oauth2"
  };

  /**
   * 历史后端路径或已废弃页面路径，不能被 SPA fallback 接管。
   */
  public static final String[] BACKEND_RESERVED_EXACT_PATHS = {
      "/problems"
  };

  private SpaRoutes() {
  }
}
