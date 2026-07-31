package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimensionCatalog;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;

/** 已校验的用户自述批量更新参数；身份和画像类别只由服务端补全。 */
public record DeclaredProfileUpdateRequest(List<Item> updates) {

  private static final Set<String> ITEM_FIELDS = Set.of(
      LearnerDeclaredProfileToolContracts.UPDATE_DIMENSION,
      LearnerDeclaredProfileToolContracts.UPDATE_STATEMENT,
      LearnerDeclaredProfileToolContracts.UPDATE_INTENT);

  public DeclaredProfileUpdateRequest {
    if (updates == null || updates.isEmpty()) {
      throw new IllegalArgumentException("Declared profile updates must not be empty");
    }
    updates = List.copyOf(updates);
    Set<LearnerMemoryClaimDimension> dimensions = EnumSet.noneOf(LearnerMemoryClaimDimension.class);
    for (Item update : updates) {
      if (update == null || !dimensions.add(update.dimension())) {
        throw new IllegalArgumentException("Declared profile updates must use unique dimensions");
      }
    }
  }

  public static DeclaredProfileUpdateRequest fromJson(JsonNode arguments) {
    if (arguments == null || !arguments.isObject() || arguments.size() != 1
        || !arguments.has(LearnerDeclaredProfileToolContracts.ARGUMENT_UPDATES)) {
      throw new IllegalArgumentException("Declared profile tool arguments are invalid");
    }
    JsonNode updates = arguments.path(LearnerDeclaredProfileToolContracts.ARGUMENT_UPDATES);
    if (!updates.isArray() || updates.isEmpty()) {
      throw new IllegalArgumentException("Declared profile tool updates are invalid");
    }
    List<Item> parsed = new ArrayList<>();
    for (JsonNode update : updates) {
      parsed.add(parseItem(update));
    }
    return new DeclaredProfileUpdateRequest(parsed);
  }

  private static Item parseItem(JsonNode update) {
    if (update == null || !update.isObject() || update.size() != ITEM_FIELDS.size()) {
      throw new IllegalArgumentException("Declared profile update item is invalid");
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    update.fieldNames().forEachRemaining(names::add);
    if (!names.equals(ITEM_FIELDS)) {
      throw new IllegalArgumentException("Declared profile update item has unsupported fields");
    }
    LearnerMemoryClaimDimension dimension = enumValue(
        update.path(LearnerDeclaredProfileToolContracts.UPDATE_DIMENSION), LearnerMemoryClaimDimension.class);
    if (!LearnerMemoryClaimDimensionCatalog.declaredDimensions().contains(dimension)) {
      throw new IllegalArgumentException("Declared profile dimension is not allowed");
    }
    String statement = requiredText(update, LearnerDeclaredProfileToolContracts.UPDATE_STATEMENT);
    if (statement.length() > LearnerDeclaredProfileToolContracts.MAX_STATEMENT_CHARS) {
      throw new IllegalArgumentException("Declared profile statement is too long");
    }
    DeclaredProfileUpdateIntent intent = enumValue(
        update.path(LearnerDeclaredProfileToolContracts.UPDATE_INTENT), DeclaredProfileUpdateIntent.class);
    return new Item(dimension, statement, intent);
  }

  private static String requiredText(JsonNode source, String field) {
    JsonNode value = source.path(field);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("Declared profile update text is invalid");
    }
    return value.asText().trim();
  }

  private static <T extends Enum<T>> T enumValue(JsonNode value, Class<T> type) {
    if (!value.isTextual()) {
      throw new IllegalArgumentException("Declared profile update enum is invalid");
    }
    try {
      return Enum.valueOf(type, value.asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Declared profile update enum is not allowed", exception);
    }
  }

  public record Item(
      LearnerMemoryClaimDimension dimension,
      String statement,
      DeclaredProfileUpdateIntent intent
  ) {
    public Item {
      if (dimension == null || statement == null || statement.isBlank() || intent == null
          || statement.trim().length() > LearnerDeclaredProfileToolContracts.MAX_STATEMENT_CHARS
          || !LearnerMemoryClaimDimensionCatalog.declaredDimensions().contains(dimension)) {
        throw new IllegalArgumentException("Invalid declared profile update item");
      }
      statement = statement.trim();
    }
  }
}
