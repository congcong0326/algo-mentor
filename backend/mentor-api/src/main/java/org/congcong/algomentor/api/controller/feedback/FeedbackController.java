package org.congcong.algomentor.api.controller.feedback;

import org.congcong.algomentor.api.controller.feedback.model.FeedbackCreateRequest;
import org.congcong.algomentor.api.controller.feedback.model.FeedbackMessageRequest;
import org.congcong.algomentor.api.controller.feedback.model.FeedbackResponseMapper;
import org.congcong.algomentor.api.feedback.service.FeedbackInput;
import org.congcong.algomentor.api.feedback.service.FeedbackUnauthenticatedException;
import org.congcong.algomentor.api.feedback.service.UserFeedbackService;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(FeedbackApiContractConstants.BASE_PATH)
public class FeedbackController {
  private final UserFeedbackService service;
  private final CurrentUserIdProvider currentUser;
  public FeedbackController(UserFeedbackService service, CurrentUserIdProvider currentUser) { this.service = service; this.currentUser = currentUser; }
  @PostMapping public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> create(@RequestBody FeedbackCreateRequest request) { return ApiResponse.success(FeedbackResponseMapper.detail(service.create(userId(), new FeedbackInput(request == null ? null : org.congcong.algomentor.api.feedback.service.FeedbackInputNormalizer.category(request.category()), request == null ? null : request.subject(), request == null ? null : request.content(), request == null ? null : request.sourcePath(), request == null ? null : request.sourceRequestId(), request == null ? null : request.sourceRunId())))); }
  @GetMapping public ApiResponse<FeedbackResponseMapper.FeedbackThreadPageResponse> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status) { return ApiResponse.success(FeedbackResponseMapper.page(service.list(userId(), page, pageSize, status), null)); }
  @GetMapping(FeedbackApiContractConstants.THREAD_ID_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> detail(@PathVariable long threadId) { return ApiResponse.success(FeedbackResponseMapper.detail(service.detail(userId(), threadId))); }
  @PostMapping(FeedbackApiContractConstants.MESSAGES_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> reply(@PathVariable long threadId, @RequestBody FeedbackMessageRequest request) { return ApiResponse.success(FeedbackResponseMapper.detail(service.reply(userId(), threadId, request == null ? null : request.content()))); }
  @PostMapping(FeedbackApiContractConstants.READ_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackReadResponse> read(@PathVariable long threadId) { return ApiResponse.success(FeedbackResponseMapper.read(service.markRead(userId(), threadId))); }
  private long userId() { return currentUser.currentUser().map(AuthenticatedUserPrincipal::userId).orElseThrow(FeedbackUnauthenticatedException::new); }
}
