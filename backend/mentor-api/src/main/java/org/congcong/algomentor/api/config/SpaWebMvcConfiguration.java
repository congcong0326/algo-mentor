package org.congcong.algomentor.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SpaWebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler(SpaRoutes.HASHED_ASSETS_PATH_PATTERN)
        .addResourceLocations(SpaRoutes.HASHED_ASSETS_RESOURCE_LOCATION)
        .setCacheControl(CacheControl.maxAge(SpaRoutes.HASHED_ASSETS_CACHE_MAX_AGE)
            .cachePublic()
            .immutable());
  }

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
