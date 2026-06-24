package org.congcong.algomentor.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaWebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    for (String route : SpaRoutes.FRONTEND_ROUTES) {
      registry.addViewController(route).setViewName(SpaRoutes.INDEX_FORWARD);
    }
    for (String routePattern : SpaRoutes.FRONTEND_ROUTE_PATTERNS) {
      registry.addViewController(routePattern).setViewName(SpaRoutes.INDEX_FORWARD);
    }
  }
}
