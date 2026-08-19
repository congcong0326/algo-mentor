package org.congcong.algomentor.api.controller.profile;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Origin;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LearnerProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    LearnerProfileControllerTest.TestConfig.class,
    LocalizedApiExceptionHandler.class
})
class LearnerProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private LearnerProfileDocumentService profileService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @BeforeEach
  void clearMockHistory() {
    clearInvocations(profileService, currentUserIdProvider);
  }

  @Test
  void profileUsesCurrentUserAndReturnsTheDocumentContract() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getDocument(42L, "zh-CN")).thenReturn(document());

    mockMvc.perform(get("/api/me/learner-profile?userId=99").header("Accept-Language", "zh-CN"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"a" + "0".repeat(63) + "\""))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.format").value("MARKDOWN_DOCUMENT_V1"))
        .andExpect(jsonPath("$.data.documentRevision").value("a" + "0".repeat(63)))
        .andExpect(jsonPath("$.data.blocks[0].type").value("HEADING"))
        .andExpect(jsonPath("$.data.blocks[1].spans[0].type").value("SUPPORTED_TEXT"))
        .andExpect(jsonPath("$.data.citationMap.1.statementRef").value("opaque-statement-ref"))
        .andExpect(jsonPath("$.data.declaredFacts").doesNotExist())
        .andExpect(jsonPath("$.data.tagAssessments").doesNotExist());

    verify(profileService).getDocument(42L, "zh-CN");
  }

  @Test
  void matchingEtagReturnsNotModifiedWithoutAResponseBody() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getDocument(42L, "zh-CN")).thenReturn(document());

    mockMvc.perform(get("/api/me/learner-profile")
            .header("Accept-Language", "zh-CN")
            .header("If-None-Match", "\"a" + "0".repeat(63) + "\""))
        .andExpect(status().isNotModified())
        .andExpect(header().string("ETag", "\"a" + "0".repeat(63) + "\""));

    verify(profileService).getDocument(42L, "zh-CN");
  }

  @Test
  void evidenceUsesCurrentUserAndOpaqueStatementRef() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getEvidence(42L, "opaque-statement-ref", "opaque-cursor", 20, "zh-CN"))
        .thenReturn(new LearnerProfileDocument.EvidencePage(List.of(), null));

    mockMvc.perform(get("/api/me/learner-profile/statements/opaque-statement-ref/evidence")
            .queryParam("cursor", "opaque-cursor")
            .header("Accept-Language", "zh-CN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

    verify(profileService).getEvidence(42L, "opaque-statement-ref", "opaque-cursor", 20, "zh-CN");
  }

  @Test
  void evidenceReturnsValidationFailureForAnInvalidLimit() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getEvidence(42L, "opaque-statement-ref", null, 21, "zh-CN"))
        .thenThrow(new LearnerProfileDocumentService.InvalidLearnerProfileDocumentRequestException(
            "evidence limit must be between 1 and 20"));

    mockMvc.perform(get("/api/me/learner-profile/statements/opaque-statement-ref/evidence")
            .queryParam("limit", "21")
            .header("Accept-Language", "zh-CN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }

  @Test
  void evidenceReturnsNotFoundForAForgedOrMismatchedReference() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(profileService.getEvidence(42L, "forged-statement-ref", null, 20, "zh-CN"))
        .thenThrow(new LearnerProfileDocumentService.LearnerProfileStatementNotFoundException());

    mockMvc.perform(get("/api/me/learner-profile/statements/forged-statement-ref/evidence")
            .header("Accept-Language", "zh-CN"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LEARNER_PROFILE_STATEMENT_NOT_FOUND"));
  }

  @Test
  void profileRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/me/learner-profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(profileService);
  }

  @Test
  void evidenceRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/me/learner-profile/statements/opaque-statement-ref/evidence"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(profileService);
  }

  private AuthenticatedUserPrincipal currentUser() {
    return new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE);
  }

  private LearnerProfileDocument document() {
    Instant updatedAt = Instant.parse("2026-07-20T12:00:00Z");
    String revision = "a" + "0".repeat(63);
    LearnerProfileDocument.Citation citation = new LearnerProfileDocument.Citation(
        1,
        "opaque-statement-ref",
        7L,
        UUID.fromString("00000000-0000-0000-0000-000000000007"),
        Origin.USER_EXPLICIT,
        "用户消息依据 1 条",
        1,
        List.of());
    return new LearnerProfileDocument(
        LearnerProfileDocument.FORMAT,
        LearnerProfileDocument.PROJECTOR_VERSION,
        "zh-CN",
        revision,
        "学习画像",
        List.of(
            new LearnerProfileDocument.Block(LearnerProfileDocument.BlockType.HEADING,
                List.of(new LearnerProfileDocument.Span(LearnerProfileDocument.SpanType.TEXT, "学习背景与目标", null))),
            new LearnerProfileDocument.Block(LearnerProfileDocument.BlockType.PARAGRAPH,
                List.of(new LearnerProfileDocument.Span(
                    LearnerProfileDocument.SpanType.SUPPORTED_TEXT, "准备后端面试。", 1)))),
        java.util.Map.of(1, citation),
        updatedAt);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    LearnerProfileDocumentService learnerProfileDocumentService() {
      return mock(LearnerProfileDocumentService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }
  }
}
