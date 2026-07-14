package org.congcong.algomentor.ai.governance.pricing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiModelPriceMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiModelPriceRow;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;

/** 模型价格配置的管理员读写服务。 */
public class AiModelPriceAdminService {

  private final AiModelPriceMapper mapper;
  private final AdminOperationAuditRecorder auditRecorder;
  private final Clock clock;

  public AiModelPriceAdminService(
      AiModelPriceMapper mapper,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock
  ) {
    this.mapper = mapper;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
  }

  public List<AiModelPrice> list() {
    return mapper.findAll().stream().map(AiModelPriceRow::toDomain).toList();
  }

  public AiModelPrice create(
      String provider,
      String model,
      BigDecimal inputPricePerMillion,
      BigDecimal cachedInputPricePerMillion,
      BigDecimal outputPricePerMillion,
      BigDecimal costMultiplier,
      Boolean enabled,
      long operatorUserId
  ) {
    AiModelPrice candidate = newPrice(
        null,
        provider,
        model,
        inputPricePerMillion,
        cachedInputPricePerMillion,
        outputPricePerMillion,
        costMultiplier,
        enabled,
        operatorUserId,
        Instant.now(clock));
    if (mapper.findByProviderAndModel(candidate.provider(), candidate.model()) != null) {
      recordFailure(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_CREATE, null,
          AiGovernanceErrorCode.AI_MODEL_PRICE_ALREADY_EXISTS);
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_ALREADY_EXISTS,
          "AI model price already exists.");
    }
    long id;
    try {
      id = mapper.insert(AiModelPriceRow.fromDomain(candidate));
    } catch (RuntimeException exception) {
      recordFailure(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_CREATE, null,
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID);
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID,
          "Failed to create AI model price.",
          exception);
    }
    AiModelPrice created = require(id);
    recordSuccess(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_CREATE, created);
    return created;
  }

  public AiModelPrice update(
      long id,
      String provider,
      String model,
      BigDecimal inputPricePerMillion,
      BigDecimal cachedInputPricePerMillion,
      BigDecimal outputPricePerMillion,
      BigDecimal costMultiplier,
      Boolean enabled,
      long operatorUserId
  ) {
    AiModelPrice existing = require(id);
    AiModelPrice candidate = newPrice(
        id,
        provider,
        model,
        inputPricePerMillion,
        cachedInputPricePerMillion,
        outputPricePerMillion,
        costMultiplier,
        enabled,
        operatorUserId,
        Instant.now(clock));
    AiModelPriceRow matching = mapper.findByProviderAndModel(candidate.provider(), candidate.model());
    if (matching != null && !matching.id().equals(id)) {
      recordFailure(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_UPDATE, Long.toString(id),
          AiGovernanceErrorCode.AI_MODEL_PRICE_ALREADY_EXISTS);
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_ALREADY_EXISTS,
          "AI model price already exists.");
    }
    try {
      if (mapper.update(AiModelPriceRow.fromDomain(candidate)) != 1) {
        throw new AiGovernanceAdminException(
            AiGovernanceErrorCode.AI_MODEL_PRICE_NOT_FOUND,
            "AI model price was not found.");
      }
    } catch (AiGovernanceAdminException exception) {
      recordFailure(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_UPDATE, Long.toString(id), exception.code());
      throw exception;
    } catch (RuntimeException exception) {
      recordFailure(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_UPDATE, Long.toString(id),
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID);
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID,
          "Failed to update AI model price.",
          exception);
    }
    AiModelPrice updated = require(id);
    recordSuccess(operatorUserId, AdminAuditAction.AI_MODEL_PRICE_UPDATE, updated);
    return updated;
  }

  private AiModelPrice newPrice(
      Long id,
      String provider,
      String model,
      BigDecimal inputPricePerMillion,
      BigDecimal cachedInputPricePerMillion,
      BigDecimal outputPricePerMillion,
      BigDecimal costMultiplier,
      Boolean enabled,
      long operatorUserId,
      Instant now
  ) {
    try {
      if (enabled == null) {
        throw new IllegalArgumentException("enabled is required");
      }
      return new AiModelPrice(
          id,
          provider,
          model,
          AiModelPrice.CURRENCY_USD,
          inputPricePerMillion,
          cachedInputPricePerMillion,
          outputPricePerMillion,
          costMultiplier,
          enabled,
          operatorUserId,
          null,
          now);
    } catch (IllegalArgumentException exception) {
      recordFailure(operatorUserId, id == null ? AdminAuditAction.AI_MODEL_PRICE_CREATE : AdminAuditAction.AI_MODEL_PRICE_UPDATE,
          id == null ? null : Long.toString(id), AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID);
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_INVALID,
          "AI model price is invalid.",
          exception);
    }
  }

  private AiModelPrice require(long id) {
    if (id < 1) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_NOT_FOUND,
          "AI model price was not found.");
    }
    AiModelPriceRow row = mapper.findById(id);
    if (row == null) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_MODEL_PRICE_NOT_FOUND,
          "AI model price was not found.");
    }
    return row.toDomain();
  }

  private void recordSuccess(long operatorUserId, AdminAuditAction action, AiModelPrice price) {
    Map<AdminAuditMetadataKey, Object> metadata = new LinkedHashMap<>();
    metadata.put(AdminAuditMetadataKey.PROVIDER, price.provider());
    metadata.put(AdminAuditMetadataKey.MODEL, price.model());
    metadata.put(AdminAuditMetadataKey.INPUT_PRICE_PER_MILLION, price.inputPricePerMillion());
    metadata.put(AdminAuditMetadataKey.CACHED_INPUT_PRICE_PER_MILLION, price.cachedInputPricePerMillion());
    metadata.put(AdminAuditMetadataKey.OUTPUT_PRICE_PER_MILLION, price.outputPricePerMillion());
    metadata.put(AdminAuditMetadataKey.COST_MULTIPLIER, price.costMultiplier());
    metadata.put(AdminAuditMetadataKey.PRICE_ENABLED, price.enabled());
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        action,
        AdminAuditTargetType.AI_MODEL_PRICE,
        Long.toString(price.id()),
        metadata));
  }

  private void recordFailure(
      long operatorUserId,
      AdminAuditAction action,
      String targetRef,
      AiGovernanceErrorCode code
  ) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        action,
        AdminAuditTargetType.AI_MODEL_PRICE,
        targetRef,
        code.name()));
  }
}
