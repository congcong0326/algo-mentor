package org.congcong.algomentor.api.controller.admin.systemprompt;

import java.util.List;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinition;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitionRegistry;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端受管理系统提示词目录、definition 详情和实际命中模拟接口。 */
@RestController
@RequestMapping("/api/admin/system-prompt-types")
public final class AdminSystemPromptTypeController {

  private final ManagedSystemPromptDefinitionRegistry definitions;
  private final ManagedSystemPromptResolver resolver;
  private final GenericPolicyManagementService managementService;

  public AdminSystemPromptTypeController(
      ManagedSystemPromptDefinitionRegistry definitions,
      ManagedSystemPromptResolver resolver,
      ObjectProvider<GenericPolicyManagementService> managementService
  ) {
    this.definitions = definitions;
    this.resolver = resolver;
    this.managementService = managementService.getIfAvailable();
  }

  @GetMapping
  public ApiResponse<SystemPromptTypeListResponse> list() {
    return ApiResponse.success(new SystemPromptTypeListResponse(definitions.definitions().stream()
        .map(this::summary)
        .toList()));
  }

  @GetMapping("/{typeCode}")
  public ApiResponse<SystemPromptTypeDetailResponse> detail(@PathVariable String typeCode) {
    ManagedSystemPromptDefinition definition = definitions.require(typeCode);
    return ApiResponse.success(SystemPromptTypeDetailResponse.from(definition));
  }

  @GetMapping("/{typeCode}/effective")
  public ApiResponse<SystemPromptEffectiveResponse> effective(
      @PathVariable String typeCode,
      @RequestParam long userId
  ) {
    ManagedSystemPromptDefinition definition = definitions.require(typeCode);
    return ApiResponse.success(SystemPromptEffectiveResponse.from(resolver.resolve(definition, userId)));
  }

  private SystemPromptTypeSummaryResponse summary(ManagedSystemPromptDefinition definition) {
    long liveCount = managementService == null ? 0 : managementService.search(
        new GenericPolicySearchQuery(definition.typeCode(), null, null, 1, 1)).total();
    return new SystemPromptTypeSummaryResponse(
        definition.typeCode(), definition.descriptor().categoryCode(), definition.descriptor().displayNameZh(),
        definition.descriptor().descriptionZh(), definition.sourceRevision(), definition.snapshotScope().name(),
        definition.sections().size(), liveCount > 0, liveCount, liveCount > 0 ? "POLICY" : "CODE_DEFAULT");
  }

  public record SystemPromptTypeListResponse(List<SystemPromptTypeSummaryResponse> items) {
  }

  public record SystemPromptTypeSummaryResponse(
      String typeCode, String categoryCode, String displayName, String description,
      String sourceRevision, String snapshotScope, int sectionCount, boolean configured,
      long livePolicyCount, String effectiveSource
  ) {
  }

  public record SystemPromptTypeDetailResponse(
      String typeCode, String sourceRevision, String snapshotScope, List<SystemPromptSectionResponse> sections
  ) {
    static SystemPromptTypeDetailResponse from(ManagedSystemPromptDefinition definition) {
      return new SystemPromptTypeDetailResponse(definition.typeCode(), definition.sourceRevision(),
          definition.snapshotScope().name(), definition.sections().stream().map(section -> new SystemPromptSectionResponse(
              section.key(), section.displayNameZh(), section.descriptionZh(), section.displayOrder(), section.required(),
              section.maxLength(), section.defaultText())).toList());
    }
  }

  public record SystemPromptSectionResponse(
      String key, String displayName, String description, int displayOrder, boolean required,
      int maxLength, String defaultText
  ) {
  }

  public record SystemPromptEffectiveResponse(
      String typeCode, String sourceRevision, String resolutionSource, Long policyId, Long policyVersion,
      String matchSource, Long matchedSubjectId, String combinedContentHash, List<SystemPromptEffectiveSection> sections
  ) {
    static SystemPromptEffectiveResponse from(ResolvedSystemPromptSnapshot snapshot) {
      return new SystemPromptEffectiveResponse(snapshot.typeCode(), snapshot.sourceRevision(),
          snapshot.resolutionSource().name(), snapshot.policyId(), snapshot.policyVersion(),
          snapshot.matchSource() == null ? null : snapshot.matchSource().name(), snapshot.matchedSubjectId(),
          snapshot.combinedContentHash(), snapshot.sections().values().stream().map(section ->
              new SystemPromptEffectiveSection(section.key(), section.text(), section.source().name(),
                  section.contentHash(), section.charCount())).toList());
    }
  }

  public record SystemPromptEffectiveSection(
      String key, String text, String source, String contentHash, int charCount
  ) {
  }
}
