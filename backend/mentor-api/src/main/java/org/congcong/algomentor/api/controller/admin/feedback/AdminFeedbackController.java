package org.congcong.algomentor.api.controller.admin.feedback;

import org.congcong.algomentor.api.controller.feedback.model.FeedbackMessageRequest;
import org.congcong.algomentor.api.controller.feedback.model.FeedbackResponseMapper;
import org.congcong.algomentor.api.controller.admin.feedback.model.AdminFeedbackStatusRequest;
import org.congcong.algomentor.api.feedback.service.AdminFeedbackService;
import org.congcong.algomentor.api.feedback.service.FeedbackUnauthenticatedException;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminFeedbackApiContractConstants.BASE_PATH)
public class AdminFeedbackController {
  private final AdminFeedbackService service;
  private final CurrentUserIdProvider currentUser;
  public AdminFeedbackController(AdminFeedbackService service, CurrentUserIdProvider currentUser) { this.service = service; this.currentUser = currentUser; }
  @GetMapping public ApiResponse<FeedbackResponseMapper.FeedbackThreadPageResponse> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String status, @RequestParam(required = false) String category, @RequestParam(required = false) Long userId, @RequestParam(defaultValue = "false") boolean unreadOnly) { var result = service.list(page, pageSize, status, category, userId, unreadOnly); return ApiResponse.success(FeedbackResponseMapper.page(result, service.usersFor(result))); }
  @GetMapping(AdminFeedbackApiContractConstants.THREAD_ID_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> detail(@PathVariable long threadId) { return ApiResponse.success(FeedbackResponseMapper.detail(service.detail(threadId))); }
  @PostMapping(AdminFeedbackApiContractConstants.MESSAGES_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> reply(@PathVariable long threadId, @RequestBody FeedbackMessageRequest request) { return ApiResponse.success(FeedbackResponseMapper.detail(service.reply(operatorId(), threadId, request == null ? null : request.content()))); }
  @PatchMapping(AdminFeedbackApiContractConstants.STATUS_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackThreadDetailResponse> status(@PathVariable long threadId, @RequestBody AdminFeedbackStatusRequest request) { return ApiResponse.success(FeedbackResponseMapper.detail(service.updateStatus(operatorId(), threadId, request == null ? null : request.status()))); }
  @PostMapping(AdminFeedbackApiContractConstants.READ_PATH) public ApiResponse<FeedbackResponseMapper.FeedbackReadResponse> read(@PathVariable long threadId) { return ApiResponse.success(FeedbackResponseMapper.read(service.markRead(threadId))); }
  private long operatorId() { return currentUser.currentUser().map(AuthenticatedUserPrincipal::userId).orElseThrow(FeedbackUnauthenticatedException::new); }
}
