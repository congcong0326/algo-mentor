package org.congcong.algomentor.policy.repository.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.policy.repository.mybatis.model.GenericPolicyRow;

public interface GenericPolicyMapper {

  void acquireTypeLock(@Param("typeCode") String typeCode);

  List<GenericPolicyRow> findLiveByTypeForUpdate(@Param("typeCode") String typeCode);

  List<GenericPolicyRow> findEnabledByType(@Param("typeCode") String typeCode);

  GenericPolicyRow findById(@Param("policyId") long policyId);

  GenericPolicyRow findByIdForUpdate(@Param("policyId") long policyId);

  List<GenericPolicyRow> search(
      @Param("typeCode") String typeCode,
      @Param("status") String status,
      @Param("keyword") String keyword,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long count(
      @Param("typeCode") String typeCode,
      @Param("status") String status,
      @Param("keyword") String keyword);

  int insert(GenericPolicyRow row);

  int update(
      @Param("policyId") long policyId,
      @Param("version") long version,
      @Param("name") String name,
      @Param("description") String description,
      @Param("status") String status,
      @Param("subjectRange") JsonNode subjectRange,
      @Param("content") JsonNode content,
      @Param("operatorUserId") long operatorUserId,
      @Param("updatedAt") Instant updatedAt);

  int markDeleted(
      @Param("policyId") long policyId,
      @Param("version") long version,
      @Param("operatorUserId") long operatorUserId,
      @Param("now") Instant now);

  int moveLivePrioritiesToTemporaryRange(@Param("typeCode") String typeCode);

  int updatePriority(
      @Param("policyId") long policyId,
      @Param("priority") int priority,
      @Param("operatorUserId") long operatorUserId,
      @Param("updatedAt") Instant updatedAt);

  int updatePriorityIfVersion(
      @Param("policyId") long policyId,
      @Param("version") long version,
      @Param("priority") int priority,
      @Param("operatorUserId") long operatorUserId,
      @Param("updatedAt") Instant updatedAt);
}
