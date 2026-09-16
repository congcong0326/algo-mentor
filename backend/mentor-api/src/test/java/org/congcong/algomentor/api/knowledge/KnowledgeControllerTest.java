package org.congcong.algomentor.api.knowledge;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeModels.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.congcong.algomentor.api.controller.knowledge.KnowledgeController;
import org.congcong.algomentor.api.controller.knowledge.KnowledgeExceptionHandler;
import org.congcong.algomentor.api.knowledge.service.KnowledgeException;
import org.congcong.algomentor.api.knowledge.service.KnowledgeService;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeControllerTest {
  private KnowledgeService service;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    service = mock(KnowledgeService.class);
    CurrentUserIdProvider users =
        () ->
            Optional.of(
                new AuthenticatedUserPrincipal(
                    7L, "test@example.test", "Test", null, List.of(), null));
    mvc =
        MockMvcBuilders.standaloneSetup(new KnowledgeController(service, users))
            .setControllerAdvice(new KnowledgeExceptionHandler())
            .build();
  }

  @Test
  void cardUsesSlugAndReturnsIndependentMarkdownAndRelations() throws Exception {
    when(service.card("stable-slug", 7L))
        .thenReturn(
            new CardDetail(
                "stable-slug",
                5,
                "问题",
                "回答",
                "## 解释\n详情",
                List.of("标签"),
                List.of(new Relation("related", "other-slug", "关联问题")),
                List.of(),
                new LearningState(false, null, null, false, null, null)));
    mvc.perform(get("/api/knowledge/cards/stable-slug"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slug").value("stable-slug"))
        .andExpect(jsonPath("$.data.explanationMarkdown").value("## 解释\n详情"))
        .andExpect(jsonPath("$.data.relations[0].slug").value("other-slug"));
  }

  @Test
  void reviewBindsSlugUuidAndCurrentUser() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(
            post("/api/knowledge/cards/stable-slug/review-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"clientAttemptId\":\""
                        + id
                        + "\",\"rating\":\"GOOD\",\"timezone\":\"UTC\"}"))
        .andExpect(status().isOk());
    verify(service).review("stable-slug", 7L, id, "GOOD", "UTC");
  }

  @Test
  void enrollmentAndPaginationBindCurrentUser() throws Exception {
    when(service.setEnrollment("stable-slug", 7L, true))
        .thenReturn(new LearningState(true, "LEARNING", null, true, null, null));
    mvc.perform(put("/api/knowledge/cards/stable-slug/review-enrollment"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enrolled").value(true));
    mvc.perform(delete("/api/knowledge/cards/stable-slug/review-enrollment"))
        .andExpect(status().isOk());
    verify(service).setEnrollment("stable-slug", 7L, false);
    mvc.perform(get("/api/knowledge/outline-nodes/5/cards")
            .param("page", "2").param("pageSize", "20").param("keyword", "Java"))
        .andExpect(status().isOk());
    verify(service).cards(5L, 7L, 2, 20, "Java");
  }

  @Test
  void errorsFollowKnowledgeContract() throws Exception {
    when(service.card("missing", 7L)).thenThrow(KnowledgeException.notFound());
    mvc.perform(get("/api/knowledge/cards/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_NOT_FOUND"));
    when(service.review(anyString(), anyLong(), any(), any(), any()))
        .thenThrow(new ReviewException("REVIEW_RATING_INVALID", "无效评级"));
    mvc.perform(
            post("/api/knowledge/cards/stable-slug/review-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"unknown\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("KNOWLEDGE_INVALID_REQUEST"));
  }
}
