package org.congcong.algomentor.ops.observability;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

public class StructuredOpsLogger {

  private static final int MAX_VALUE_LENGTH = 256;
  private static final String REDACTED_VALUE = "[REDACTED]";
  private static final List<String> ORDERED_FIELDS = List.of(
      OpsLogFields.REQUEST_ID,
      OpsLogFields.METHOD,
      OpsLogFields.PATH_TEMPLATE,
      OpsLogFields.STATUS,
      OpsLogFields.ERROR_CODE,
      OpsLogFields.EXCEPTION_TYPE,
      OpsLogFields.DURATION_MS,
      OpsLogFields.SSE_STREAM_TYPE,
      OpsLogFields.AGENT_RUN_ID,
      OpsLogFields.AGENT_SOURCE,
      OpsLogFields.AGENT_KEY,
      OpsLogFields.STEP_INDEX,
      OpsLogFields.MAX_STEPS,
      OpsLogFields.PROVIDER,
      OpsLogFields.MODEL,
      OpsLogFields.CONTEXT_PREPARATION_MS,
      OpsLogFields.TIME_TO_FIRST_EVENT_MS,
      OpsLogFields.FINISH_REASON,
      OpsLogFields.MESSAGE_COUNT,
      OpsLogFields.DECLARED_TOOL_COUNT,
      OpsLogFields.TOOL_CALL_COUNT,
      OpsLogFields.OUTPUT_CHAR_COUNT,
      OpsLogFields.INPUT_TOKENS,
      OpsLogFields.OUTPUT_TOKENS,
      OpsLogFields.CACHED_TOKENS,
      OpsLogFields.REASONING_TOKENS,
      OpsLogFields.TOTAL_TOKENS,
      OpsLogFields.TOOL_NAME,
      OpsLogFields.TOOL_CALL_ID,
      OpsLogFields.TOOL_ARGUMENTS,
      OpsLogFields.TOOL_RESULT,
      OpsLogFields.FAILURE_TYPE);
  private static final List<String> SENSITIVE_KEY_PARTS = List.of(
      "authorization",
      "cookie",
      "apikey",
      "token",
      "bearer",
      "jwt",
      "secret",
      "password",
      "credential",
      "body",
      "payload",
      "prompt",
      "completion",
      "aioutput");
  /** Token 用量是成本诊断所需的计数，不是认证凭证。 */
  private static final Set<String> SAFE_TOKEN_COUNT_KEYS = Set.of(
      "inputtokens",
      "outputtokens",
      "cachedtokens",
      "reasoningtokens",
      "totaltokens");

  public String format(OpsLogEventType eventType, Map<String, ?> fields) {
    Objects.requireNonNull(eventType, "eventType must not be null");

    StringBuilder message = new StringBuilder()
        .append(OpsLogFields.EVENT_TYPE)
        .append('=')
        .append(eventType.logValue());
    if (fields == null || fields.isEmpty()) {
      return message.toString();
    }

    List<Map.Entry<String, ?>> entries = orderedEntries(fields);
    for (Map.Entry<String, ?> entry : entries) {
      String key = entry.getKey();
      if (key == null || OpsLogFields.EVENT_TYPE.equals(key)) {
        continue;
      }
      message.append(' ')
          .append(sanitizeKey(key))
          .append('=')
          .append(formatValue(key, entry.getValue()));
    }
    return message.toString();
  }

  public void info(Logger log, OpsLogEventType eventType, Map<String, ?> fields) {
    Objects.requireNonNull(log, "log must not be null")
        .info(format(eventType, fields));
  }

  public void warn(
      Logger log,
      OpsLogEventType eventType,
      Map<String, ?> fields,
      Throwable throwable) {
    Objects.requireNonNull(log, "log must not be null");
    String message = format(eventType, fields);
    if (throwable == null) {
      log.warn(message);
      return;
    }
    log.warn(message, throwable);
  }

  private static List<Map.Entry<String, ?>> orderedEntries(Map<String, ?> fields) {
    Set<String> remainingKeys = new LinkedHashSet<>(fields.keySet());
    List<Map.Entry<String, ?>> entries = new ArrayList<>();

    for (String fieldName : ORDERED_FIELDS) {
      if (remainingKeys.remove(fieldName)) {
        entries.add(entry(fieldName, fields.get(fieldName)));
      }
    }

    remainingKeys.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.naturalOrder())
        .map(key -> entry(key, fields.get(key)))
        .forEach(entries::add);
    return entries;
  }

  private static Map.Entry<String, ?> entry(String key, Object value) {
    return new AbstractMap.SimpleImmutableEntry<>(key, value);
  }

  private static String formatValue(String key, Object value) {
    if (isSensitiveKey(key)) {
      return REDACTED_VALUE;
    }
    if (value == null) {
      return "null";
    }
    return sanitizeValue(truncate(stringValue(value)));
  }

  private static boolean isSensitiveKey(String key) {
    String normalizedKey = key.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]", "");
    if (SAFE_TOKEN_COUNT_KEYS.contains(normalizedKey)) {
      return false;
    }
    return SENSITIVE_KEY_PARTS.stream().anyMatch(normalizedKey::contains);
  }

  private static String stringValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return mapValue(map);
    }
    if (value instanceof Collection<?> collection) {
      return collectionValue(collection);
    }
    Class<?> valueClass = value.getClass();
    if (!valueClass.isArray()) {
      return String.valueOf(value);
    }

    int length = Array.getLength(value);
    List<String> items = new ArrayList<>(length);
    for (int index = 0; index < length; index += 1) {
      items.add(nestedStringValue(Array.get(value, index)));
    }
    return "[" + String.join(",", items) + "]";
  }

  private static String mapValue(Map<?, ?> map) {
    List<String> entries = new ArrayList<>(map.size());
    map.entrySet().stream()
        .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
        .forEach(entry -> {
          String key = String.valueOf(entry.getKey());
          String value = isSensitiveKey(key)
              ? REDACTED_VALUE
              : nestedStringValue(entry.getValue());
          entries.add(sanitizeKey(key) + "=" + value);
        });
    return "{" + String.join(",", entries) + "}";
  }

  private static String collectionValue(Collection<?> collection) {
    List<String> items = new ArrayList<>(collection.size());
    for (Object item : collection) {
      items.add(nestedStringValue(item));
    }
    return "[" + String.join(",", items) + "]";
  }

  private static String nestedStringValue(Object value) {
    return value == null ? "null" : stringValue(value);
  }

  private static String truncate(String value) {
    if (value.length() <= MAX_VALUE_LENGTH) {
      return value;
    }
    return value.substring(0, MAX_VALUE_LENGTH);
  }

  private static String sanitizeValue(String value) {
    return value.replaceAll("\\s+", "_");
  }

  private static String sanitizeKey(String key) {
    return key.replaceAll("[^A-Za-z0-9_.-]", "_");
  }

}
