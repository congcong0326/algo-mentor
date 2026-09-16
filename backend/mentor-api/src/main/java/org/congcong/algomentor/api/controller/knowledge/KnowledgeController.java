package org.congcong.algomentor.api.controller.knowledge;

import java.util.UUID;
import org.congcong.algomentor.api.knowledge.service.KnowledgeService;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(org.congcong.algomentor.api.knowledge.model.KnowledgeContract.API)
public class KnowledgeController {
  private final KnowledgeService service;
  private final CurrentUserIdProvider users;

  public KnowledgeController(KnowledgeService s, CurrentUserIdProvider u) {
    service = s;
    users = u;
  }

  private long user() {
    return users
        .currentUser()
        .orElseThrow(() -> new IllegalStateException("Authentication required"))
        .userId();
  }

  @GetMapping("/topics")
  public ApiResponse<?> topics() {
    return ApiResponse.success(service.topics(user()));
  }

  @GetMapping("/outline-nodes/{id}/tree")
  public ApiResponse<?> tree(@PathVariable long id) {
    return ApiResponse.success(service.tree(id, user()));
  }

  @GetMapping("/outline-nodes/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.success(service.detail(id, user()));
  }

  @GetMapping("/outline-nodes/{id}/cards")
  public ApiResponse<?> cards(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.cards(id, user(), page, pageSize));
  }

  @GetMapping("/outline-nodes/{id}/articles")
  public ApiResponse<?> articles(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.articles(id, page, pageSize));
  }

  @GetMapping("/articles/{id}")
  public ApiResponse<?> article(@PathVariable long id) {
    return ApiResponse.success(service.article(id));
  }

  @GetMapping("/cards/{slug}")
  public ApiResponse<?> card(@PathVariable String slug) {
    return ApiResponse.success(service.card(slug, user()));
  }

  @GetMapping("/cards/{slug}/review-preview")
  public ApiResponse<?> preview(
      @PathVariable String slug, @RequestParam(defaultValue = "UTC") String timezone) {
    return ApiResponse.success(service.preview(slug, user(), timezone));
  }

  @PostMapping("/cards/{slug}/review-attempts")
  public ApiResponse<?> review(@PathVariable String slug, @RequestBody ReviewRequest r) {
    return ApiResponse.success(
        service.review(slug, user(), r.clientAttemptId(), r.rating(), r.timezone()));
  }

  @GetMapping("/review/summary")
  public ApiResponse<?> summary() {
    return ApiResponse.success(service.summary(user()));
  }

  @GetMapping("/review/cards")
  public ApiResponse<?> reviewCards(
      @RequestParam(defaultValue = "ALL") String filter,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return ApiResponse.success(service.reviewCards(user(), filter, page, pageSize));
  }

  @GetMapping("/review/next")
  public ApiResponse<?> next() {
    return ApiResponse.success(service.next(user()));
  }

  public record ReviewRequest(UUID clientAttemptId, String rating, String timezone) {}
}
