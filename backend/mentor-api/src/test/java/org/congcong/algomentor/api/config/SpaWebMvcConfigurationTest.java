package org.congcong.algomentor.api.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SpaWebMvcConfigurationTest.TestApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class SpaWebMvcConfigurationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void forwardsFrontendRoutesToIndexHtml() throws Exception {
    for (String route : SpaRoutes.FRONTEND_ROUTES) {
      mockMvc.perform(get(route))
          .andExpect(status().isOk())
          .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));
    }
  }

  @Test
  void forwardsPublishedProblemIndexToStaticHtml() throws Exception {
    mockMvc.perform(get("/problems"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/problems/index.html"));
  }

  @Test
  void forwardsPublishedProblemPagesButRejectsUnknownSlugs() throws Exception {
    mockMvc.perform(get("/problems/two-sum"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/problems/two-sum/index.html"));

    mockMvc.perform(get("/en/problems/two-sum"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/en/problems/two-sum/index.html"));

    mockMvc.perform(get("/problems/unknown-slug"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));

    mockMvc.perform(get("/en/problems/cn-only"))
        .andExpect(status().isNotFound());
  }

  @Test
  void forwardsNestedLearningPlanRoutesToIndexHtml() throws Exception {
    mockMvc.perform(get("/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));

    mockMvc.perform(get("/learning-plans/900/phases/1/problems/two-sum/chat"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));
  }

  @Test
  void forwardsMistakeRoutesToIndexHtml() throws Exception {
    mockMvc.perform(get("/mistakes"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));

    mockMvc.perform(get("/mistakes/review"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));
  }

  @Test
  void forwardsNewExtensionlessFrontendRoutesToIndexHtml() throws Exception {
    mockMvc.perform(get("/future-feature/detail"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/" + SpaRoutes.INDEX_HTML));
  }

  @Test
  void doesNotCaptureApiRoutes() throws Exception {
    mockMvc.perform(get("/api/not-a-page"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));
  }

  @Test
  void doesNotCaptureMissingAssets() throws Exception {
    mockMvc.perform(get("/assets/missing.js"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));
  }

  @Test
  void cachesHashedViteAssetsForOneYear() throws Exception {
    mockMvc.perform(get("/assets/application-4fd2a8b9.js"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
        .andExpect(header().string("Cache-Control", containsString("public")))
        .andExpect(header().string("Cache-Control", containsString("immutable")));
  }

  @Test
  void requiresRevalidationForFixedUrlStaticResources() throws Exception {
    mockMvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-cache")));

    mockMvc.perform(get("/favicon.svg"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", containsString("no-cache")));
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration({
      JacksonAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      WebMvcAutoConfiguration.class,
      ErrorMvcAutoConfiguration.class
  })
  @Import({SpaWebMvcConfiguration.class, SpaFallbackController.class, SeoPageController.class, PrivatePageRobotsFilter.class})
  static class TestApplication {
  }
}
