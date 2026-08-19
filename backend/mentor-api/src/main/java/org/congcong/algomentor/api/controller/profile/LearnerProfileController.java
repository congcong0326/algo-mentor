package org.congcong.algomentor.api.controller.profile;

import java.util.Locale;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearnerProfileController {

  private final LearnerProfileDocumentService profileService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public LearnerProfileController(
      LearnerProfileDocumentService profileService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.profileService = profileService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ME_LEARNER_PROFILE_PATH)
  public ResponseEntity<ApiResponse<LearnerProfileDocument>> profile(
      @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
      Locale locale) {
    LearnerProfileDocument profile = profileService.getDocument(requireCurrentUserId(), localeValue(locale));
    String etag = '"' + profile.documentRevision() + '"';
    if (etag.equals(ifNoneMatch)) {
      return ResponseEntity.status(304).eTag(profile.documentRevision()).build();
    }
    return ResponseEntity.ok().eTag(profile.documentRevision()).body(ApiResponse.success(profile));
  }

  @GetMapping(ApiContractConstants.ME_LEARNER_PROFILE_STATEMENT_EVIDENCE_PATH)
  public ApiResponse<LearnerProfileDocument.EvidencePage> evidence(
      @PathVariable String statementRef,
      @RequestParam(name = ApiContractConstants.LEARNER_PROFILE_EVIDENCE_CURSOR_PARAM, required = false) String cursor,
      @RequestParam(name = ApiContractConstants.LEARNER_PROFILE_EVIDENCE_LIMIT_PARAM, defaultValue = "20") int limit,
      Locale locale) {
    return ApiResponse.success(profileService.getEvidence(
        requireCurrentUserId(), statementRef, cursor, limit, localeValue(locale)));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new LearnerProfileUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private String localeValue(Locale locale) {
    return ProblemLocale.parse(locale == null ? null : locale.toLanguageTag()).value();
  }
}
