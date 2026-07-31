package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;

/** 用户自述画像工具返回给 Agent 的无内部错误细节结果。 */
public record DeclaredProfileUpdateResult(Status status, String message, List<Item> items) {

  public DeclaredProfileUpdateResult {
    if (status == null || message == null || message.isBlank() || items == null) {
      throw new IllegalArgumentException("Invalid declared profile update result");
    }
    items = List.copyOf(items);
  }

  public static DeclaredProfileUpdateResult failed(List<LearnerMemoryClaimDimension> dimensions) {
    List<Item> items = (dimensions == null ? List.<LearnerMemoryClaimDimension>of() : dimensions).stream()
        .map(dimension -> new Item(dimension, ItemStatus.FAILED, ""))
        .toList();
    return new DeclaredProfileUpdateResult(Status.FAILED, LearnerDeclaredProfileToolContracts.MESSAGE_FAILED, items);
  }

  public JsonNode toJson(ObjectMapper objectMapper) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put(LearnerDeclaredProfileToolContracts.RESULT_FIELD_TYPE, LearnerDeclaredProfileToolContracts.RESULT_TYPE);
    root.put(LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS, status.name());
    root.put(LearnerDeclaredProfileToolContracts.RESULT_FIELD_MESSAGE, message);
    ArrayNode resultItems = root.putArray(LearnerDeclaredProfileToolContracts.RESULT_FIELD_ITEMS);
    for (Item item : items) {
      ObjectNode jsonItem = resultItems.addObject();
      jsonItem.put(LearnerDeclaredProfileToolContracts.RESULT_ITEM_DIMENSION, item.dimension().name());
      jsonItem.put(LearnerDeclaredProfileToolContracts.RESULT_ITEM_STATUS, item.status().name());
      jsonItem.put(LearnerDeclaredProfileToolContracts.RESULT_ITEM_CONTENT_SUMMARY, item.contentSummary());
    }
    return root;
  }

  public enum Status {
    UPDATED,
    NO_CHANGE,
    FAILED
  }

  public enum ItemStatus {
    APPLIED,
    NO_CHANGE,
    FAILED
  }

  public record Item(LearnerMemoryClaimDimension dimension, ItemStatus status, String contentSummary) {
    public Item {
      if (dimension == null || status == null || contentSummary == null) {
        throw new IllegalArgumentException("Invalid declared profile update result item");
      }
    }
  }
}
