package org.congcong.algomentor.api.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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

  @SpringBootConfiguration
  @ImportAutoConfiguration({
      JacksonAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      WebMvcAutoConfiguration.class,
      ErrorMvcAutoConfiguration.class
  })
  @Import(SpaWebMvcConfiguration.class)
  static class TestApplication {
  }
}
