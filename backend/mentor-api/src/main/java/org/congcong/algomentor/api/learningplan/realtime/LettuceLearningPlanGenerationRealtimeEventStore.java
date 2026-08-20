package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.api.config.LearningPlanGenerationRealtimeStreamProperties;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationConstants;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lettuce 实现：XADD 异步提交，有限阻塞 XREAD 使用独立连接。 */
public final class LettuceLearningPlanGenerationRealtimeEventStore
    implements LearningPlanGenerationRealtimeEventStore, LearningPlanDraftRevisionRealtimeEventStore, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LettuceLearningPlanGenerationRealtimeEventStore.class);
  private static final String APPEND_OPERATION = "append";
  private static final String READ_OPERATION = "read";
  private static final String EXPIRE_OPERATION = "expire";
  private static final String CONNECT_OPERATION = "connect";
  private static final String GENERATION_RESOURCE_TYPE = "generation";
  private static final String REVISION_RESOURCE_TYPE = "revision";
  private static final String SUCCESS = "success";
  private static final String FAILURE = "failure";

  private final LearningPlanGenerationRealtimeStreamProperties properties;
  private final ObjectMapper objectMapper;
  private final LearningPlanGenerationRealtimeEventPayloadMapper payloadMapper;
  private final LearningPlanDraftRevisionRealtimeEventPayloadMapper revisionPayloadMapper;
  private final LearningPlanGenerationRealtimeMetrics metrics;
  private final RedisClient client;
  private final CompletableFuture<StatefulRedisConnection<String, String>> writeConnection;
  private final Semaphore readConnections;
  private final ConcurrentMap<String, AtomicLong> nextSequences = new ConcurrentHashMap<>();

  public LettuceLearningPlanGenerationRealtimeEventStore(
      LearningPlanGenerationRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      LearningPlanGenerationRealtimeEventPayloadMapper payloadMapper,
      LearningPlanGenerationRealtimeMetrics metrics
  ) {
    this(properties, objectMapper, payloadMapper, new LearningPlanDraftRevisionRealtimeEventPayloadMapper(), metrics);
  }

  public LettuceLearningPlanGenerationRealtimeEventStore(
      LearningPlanGenerationRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      LearningPlanGenerationRealtimeEventPayloadMapper payloadMapper,
      LearningPlanDraftRevisionRealtimeEventPayloadMapper revisionPayloadMapper,
      LearningPlanGenerationRealtimeMetrics metrics
  ) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.properties.validate();
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.payloadMapper = Objects.requireNonNull(payloadMapper, "payloadMapper");
    this.revisionPayloadMapper = Objects.requireNonNull(revisionPayloadMapper, "revisionPayloadMapper");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.client = RedisClient.create(redisUri(properties));
    this.client.setOptions(ClientOptions.builder()
        .autoReconnect(true)
        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
        .socketOptions(SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build())
        .build());
    this.readConnections = new Semaphore(properties.getMaxReadConnections(), true);
    this.writeConnection = client.connectAsync(StringCodec.UTF8, redisUri(properties)).toCompletableFuture();
    this.writeConnection.whenComplete((ignored, failure) -> {
      metrics.record(GENERATION_RESOURCE_TYPE, CONNECT_OPERATION, failure == null ? SUCCESS : FAILURE);
      if (failure != null) {
        log.warn("Learning plan generation realtime Redis write connection unavailable. exceptionType={}",
            failure.getClass().getSimpleName());
      }
    });
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public void append(long draftId, LearningPlanDraftGenerationEvent event) {
    LearningPlanGenerationRealtimePayload payload = payloadMapper.map(draftId, event).orElse(null);
    if (payload == null) {
      return;
    }
    long sequence = nextSequences.computeIfAbsent("generation:" + draftId, ignored -> new AtomicLong(1L)).getAndIncrement();
    String serialized = envelope(payload);
    StatefulRedisConnection<String, String> connection = writeConnection.getNow(null);
    if (connection == null) {
      recordFailure(GENERATION_RESOURCE_TYPE, APPEND_OPERATION, null);
      return;
    }
    try {
      RedisAsyncCommands<String, String> commands = connection.async();
      RedisFuture<String> append = commands.xadd(
          LearningPlanGenerationRealtimeProtocol.streamKey(draftId),
          new XAddArgs().id(sequence + "-0"),
          Map.of(LearningPlanGenerationRealtimeProtocol.STREAM_FIELD_ENVELOPE, serialized));
      append.whenComplete((ignored, failure) -> {
        if (failure == null) {
          metrics.record(GENERATION_RESOURCE_TYPE, APPEND_OPERATION, SUCCESS);
        } else {
          recordFailure(GENERATION_RESOURCE_TYPE, APPEND_OPERATION, failure);
        }
      });
      RedisFuture<Boolean> expire = commands.expire(
          LearningPlanGenerationRealtimeProtocol.streamKey(draftId), retention(event));
      expire.whenComplete((applied, failure) -> {
        if (failure == null && Boolean.TRUE.equals(applied)) {
          metrics.record(GENERATION_RESOURCE_TYPE, EXPIRE_OPERATION, SUCCESS);
        } else {
          recordFailure(GENERATION_RESOURCE_TYPE, EXPIRE_OPERATION, failure);
        }
      });
    } catch (RuntimeException failure) {
      recordFailure(GENERATION_RESOURCE_TYPE, APPEND_OPERATION, failure);
    }
  }

  @Override
  public List<LearningPlanGenerationRealtimeEvent> readAfter(long draftId, String after, boolean block) {
    String cursor = LearningPlanGenerationRealtimeCursor.normalizeAfter(after);
    boolean acquired = false;
    try {
      if (!readConnections.tryAcquire()) {
        throw new LearningPlanGenerationRealtimeUnavailableException(
            "Learning plan generation realtime read connection capacity is exhausted");
      }
      acquired = true;
      try (StatefulRedisConnection<String, String> connection = client.connect()) {
        metrics.record(GENERATION_RESOURCE_TYPE, CONNECT_OPERATION, SUCCESS);
        RedisCommands<String, String> commands = connection.sync();
        String key = LearningPlanGenerationRealtimeProtocol.streamKey(draftId);
        List<StreamMessage<String, String>> entries = block
            ? commands.xread(new XReadArgs().block(properties.getReadBlock()), XReadArgs.StreamOffset.from(key, cursor))
            : commands.xread(XReadArgs.StreamOffset.from(key, cursor));
        metrics.record(GENERATION_RESOURCE_TYPE, READ_OPERATION, SUCCESS);
        return entries == null ? List.of() : entries.stream().map(entry -> decode(draftId, entry)).toList();
      }
    } catch (RuntimeException failure) {
      recordFailure(GENERATION_RESOURCE_TYPE, READ_OPERATION, failure);
      throw failure instanceof LearningPlanGenerationRealtimeUnavailableException unavailable
          ? unavailable
          : new LearningPlanGenerationRealtimeUnavailableException(
              "Learning plan generation realtime Redis read failed", failure);
    } finally {
      if (acquired) {
        readConnections.release();
      }
    }
  }

  @Override
  public void append(long draftId, long revisionId, LearningPlanDraftRevisionGenerationEvent event) {
    LearningPlanDraftRevisionRealtimeEventPayloadMapper.Payload payload = revisionPayloadMapper
        .map(draftId, revisionId, event).orElse(null);
    if (payload == null) {
      return;
    }
    long sequence = nextSequences.computeIfAbsent("revision:" + revisionId, ignored -> new AtomicLong(1L))
        .getAndIncrement();
    StatefulRedisConnection<String, String> connection = writeConnection.getNow(null);
    if (connection == null) {
      recordFailure(REVISION_RESOURCE_TYPE, APPEND_OPERATION, null);
      return;
    }
    String key = LearningPlanDraftRevisionRealtimeProtocol.streamKey(revisionId);
    try {
      RedisAsyncCommands<String, String> commands = connection.async();
      RedisFuture<String> append = commands.xadd(key, new XAddArgs().id(sequence + "-0"), Map.of(
          LearningPlanDraftRevisionRealtimeProtocol.STREAM_FIELD_ENVELOPE, revisionEnvelope(payload)));
      append.whenComplete((ignored, failure) -> {
        if (failure == null) {
          metrics.record(REVISION_RESOURCE_TYPE, APPEND_OPERATION, SUCCESS);
        } else {
          recordFailure(REVISION_RESOURCE_TYPE, APPEND_OPERATION, failure);
        }
      });
      RedisFuture<Boolean> expire = commands.expire(key, revisionRetention(event));
      expire.whenComplete((applied, failure) -> {
        if (failure == null && Boolean.TRUE.equals(applied)) {
          metrics.record(REVISION_RESOURCE_TYPE, EXPIRE_OPERATION, SUCCESS);
        } else {
          recordFailure(REVISION_RESOURCE_TYPE, EXPIRE_OPERATION, failure);
        }
      });
    } catch (RuntimeException failure) {
      recordFailure(REVISION_RESOURCE_TYPE, APPEND_OPERATION, failure);
    }
  }

  @Override
  public List<LearningPlanDraftRevisionRealtimeEvent> readAfter(
      long draftId, long revisionId, String after, boolean block) {
    String cursor = LearningPlanGenerationRealtimeCursor.normalizeAfter(after);
    boolean acquired = false;
    try {
      if (!readConnections.tryAcquire()) {
        throw new LearningPlanGenerationRealtimeUnavailableException(
            "Learning plan revision realtime read connection capacity is exhausted");
      }
      acquired = true;
      try (StatefulRedisConnection<String, String> connection = client.connect()) {
        metrics.record(REVISION_RESOURCE_TYPE, CONNECT_OPERATION, SUCCESS);
        RedisCommands<String, String> commands = connection.sync();
        String key = LearningPlanDraftRevisionRealtimeProtocol.streamKey(revisionId);
        List<StreamMessage<String, String>> entries = block
            ? commands.xread(new XReadArgs().block(properties.getReadBlock()), XReadArgs.StreamOffset.from(key, cursor))
            : commands.xread(XReadArgs.StreamOffset.from(key, cursor));
        metrics.record(REVISION_RESOURCE_TYPE, READ_OPERATION, SUCCESS);
        return entries == null ? List.of() : entries.stream()
            .map(entry -> decodeRevision(draftId, revisionId, entry)).toList();
      }
    } catch (RuntimeException failure) {
      recordFailure(REVISION_RESOURCE_TYPE, READ_OPERATION, failure);
      throw failure instanceof LearningPlanGenerationRealtimeUnavailableException unavailable
          ? unavailable
          : new LearningPlanGenerationRealtimeUnavailableException(
              "Learning plan revision realtime Redis read failed", failure);
    } finally {
      if (acquired) {
        readConnections.release();
      }
    }
  }

  @Override
  public void close() {
    try {
      writeConnection.thenAccept(StatefulRedisConnection::close);
    } finally {
      client.shutdown(properties.getShutdownTimeout(), properties.getShutdownTimeout());
    }
  }

  private String envelope(LearningPlanGenerationRealtimePayload payload) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          LearningPlanGenerationRealtimeProtocol.ENVELOPE_VERSION_FIELD,
          LearningPlanGenerationRealtimeProtocol.ENVELOPE_VERSION,
          LearningPlanGenerationRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD, payload.eventName(),
          LearningPlanGenerationRealtimeProtocol.ENVELOPE_DATA_FIELD, payload.data()));
    } catch (JsonProcessingException failure) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime envelope serialization failed", failure);
    }
  }

  private String revisionEnvelope(LearningPlanDraftRevisionRealtimeEventPayloadMapper.Payload payload) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_VERSION_FIELD,
          LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_VERSION,
          LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD, payload.eventName(),
          LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_DATA_FIELD, payload.data()));
    } catch (JsonProcessingException failure) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan revision realtime envelope serialization failed", failure);
    }
  }

  private LearningPlanGenerationRealtimeEvent decode(long draftId, StreamMessage<String, String> entry) {
    String serialized = entry.getBody().get(LearningPlanGenerationRealtimeProtocol.STREAM_FIELD_ENVELOPE);
    if (serialized == null || serialized.isBlank()) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry is malformed");
    }
    try {
      JsonNode envelope = objectMapper.readTree(serialized);
      if (envelope.path(LearningPlanGenerationRealtimeProtocol.ENVELOPE_VERSION_FIELD).asInt()
          != LearningPlanGenerationRealtimeProtocol.ENVELOPE_VERSION) {
        throw new LearningPlanGenerationRealtimeUnavailableException(
            "Learning plan generation realtime Redis entry version is unsupported");
      }
      JsonNode eventName = envelope.get(LearningPlanGenerationRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD);
      JsonNode data = envelope.get(LearningPlanGenerationRealtimeProtocol.ENVELOPE_DATA_FIELD);
      if (eventName == null || !eventName.isTextual() || data == null || !data.isObject()) {
        throw new LearningPlanGenerationRealtimeUnavailableException(
            "Learning plan generation realtime Redis entry is malformed");
      }
      validatePublicPayload(draftId, eventName.asText(), data);
      return new LearningPlanGenerationRealtimeEvent(entry.getId(), eventName.asText(), data);
    } catch (JsonProcessingException failure) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry cannot be decoded", failure);
    }
  }

  private void validatePublicPayload(long draftId, String eventName, JsonNode data) {
    if (!data.path(LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID).canConvertToLong()
        || data.path(LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID).longValue() != draftId) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry draft id is invalid");
    }
    switch (eventName) {
      case LearningPlanGenerationRealtimeProtocol.WORK_START -> {
        requireExactFields(data, LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID,
            LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE);
        requireExactValue(data, LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE,
            LearningPlanDraftGenerationConstants.WORK_STARTED_MESSAGE);
      }
      case LearningPlanGenerationRealtimeProtocol.WORK_PROGRESS -> {
        requireExactFields(data, LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID,
            LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE);
        requireExactValue(data, LearningPlanGenerationRealtimeProtocol.DATA_MESSAGE,
            LearningPlanDraftGenerationConstants.WORK_PROGRESS_MESSAGE);
      }
      case LearningPlanGenerationRealtimeProtocol.WORK_TOOL_START,
          LearningPlanGenerationRealtimeProtocol.WORK_TOOL_END -> {
        requireExactFields(data, LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID,
            LearningPlanGenerationRealtimeProtocol.DATA_TOOL_NAME);
        requireWhitelistedTool(data.get(LearningPlanGenerationRealtimeProtocol.DATA_TOOL_NAME).asText());
      }
      case LearningPlanGenerationRealtimeProtocol.DRAFT_COMPLETED -> requireExactFields(
          data, LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID);
      case LearningPlanGenerationRealtimeProtocol.DRAFT_FAILED -> {
        requireExactFields(data, LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID,
            LearningPlanGenerationRealtimeProtocol.DATA_CODE);
        requirePublicFailureCode(data.get(LearningPlanGenerationRealtimeProtocol.DATA_CODE).asText());
      }
      default -> throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis event is not public");
    }
  }

  private LearningPlanDraftRevisionRealtimeEvent decodeRevision(
      long draftId, long revisionId, StreamMessage<String, String> entry) {
    String serialized = entry.getBody().get(LearningPlanDraftRevisionRealtimeProtocol.STREAM_FIELD_ENVELOPE);
    if (serialized == null || serialized.isBlank()) {
      throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime Redis entry is malformed");
    }
    try {
      JsonNode envelope = objectMapper.readTree(serialized);
      JsonNode eventName = envelope.get(LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD);
      JsonNode data = envelope.get(LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_DATA_FIELD);
      if (envelope.size() != 3
          || envelope.path(LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_VERSION_FIELD).asInt()
              != LearningPlanDraftRevisionRealtimeProtocol.ENVELOPE_VERSION
          || eventName == null || !eventName.isTextual() || data == null || !data.isObject()) {
        throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime Redis entry is malformed");
      }
      validateRevisionPublicPayload(draftId, revisionId, eventName.asText(), data);
      return new LearningPlanDraftRevisionRealtimeEvent(entry.getId(), eventName.asText(), data);
    } catch (JsonProcessingException failure) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan revision realtime Redis entry cannot be decoded", failure);
    }
  }

  private void validateRevisionPublicPayload(long draftId, long revisionId, String eventName, JsonNode data) {
    if (!data.path(LearningPlanDraftRevisionRealtimeProtocol.DATA_DRAFT_ID).canConvertToLong()
        || data.path(LearningPlanDraftRevisionRealtimeProtocol.DATA_DRAFT_ID).longValue() != draftId
        || !data.path(LearningPlanDraftRevisionRealtimeProtocol.DATA_REVISION_ID).canConvertToLong()
        || data.path(LearningPlanDraftRevisionRealtimeProtocol.DATA_REVISION_ID).longValue() != revisionId) {
      throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime identity is invalid");
    }
    switch (eventName) {
      case LearningPlanDraftRevisionRealtimeProtocol.WORK_START -> requireRevisionExactFields(
          data, LearningPlanDraftRevisionGenerationConstants.WORK_STARTED_MESSAGE,
          LearningPlanDraftRevisionRealtimeProtocol.DATA_MESSAGE);
      case LearningPlanDraftRevisionRealtimeProtocol.WORK_PROGRESS -> requireRevisionExactFields(
          data, LearningPlanDraftRevisionGenerationConstants.WORK_PROGRESS_MESSAGE,
          LearningPlanDraftRevisionRealtimeProtocol.DATA_MESSAGE);
      case LearningPlanDraftRevisionRealtimeProtocol.REVISION_COMPLETED -> requireRevisionFields(data);
      case LearningPlanDraftRevisionRealtimeProtocol.REVISION_FAILED,
          LearningPlanDraftRevisionRealtimeProtocol.REVISION_SUPERSEDED -> {
        requireRevisionFields(data, LearningPlanDraftRevisionRealtimeProtocol.DATA_CODE);
        if (!LearningPlanDraftRevisionGenerationConstants.PUBLIC_FAILURE_CODES.contains(
            data.get(LearningPlanDraftRevisionRealtimeProtocol.DATA_CODE).asText())) {
          throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime failure code is invalid");
        }
      }
      default -> throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan revision realtime Redis event is not public");
    }
  }

  private void requireRevisionExactFields(JsonNode data, String expected, String field) {
    requireRevisionFields(data, field);
    if (!expected.equals(data.get(field).asText())) {
      throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime public value is invalid");
    }
  }

  private void requireRevisionFields(JsonNode data, String... fields) {
    if (data.size() != 2 + fields.length
        || !data.hasNonNull(LearningPlanDraftRevisionRealtimeProtocol.DATA_DRAFT_ID)
        || !data.hasNonNull(LearningPlanDraftRevisionRealtimeProtocol.DATA_REVISION_ID)) {
      throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime fields are invalid");
    }
    for (String field : fields) {
      if (!data.hasNonNull(field) || !data.get(field).isTextual()) {
        throw new LearningPlanGenerationRealtimeUnavailableException("Learning plan revision realtime fields are invalid");
      }
    }
  }

  private void requireExactValue(JsonNode data, String field, String expected) {
    if (!expected.equals(data.get(field).asText())) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry has an invalid public value");
    }
  }

  private void requireWhitelistedTool(String toolName) {
    if (!LearningPlanDraftGenerationConstants.PUBLIC_TOOL_NAMES.contains(toolName)) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry has an unapproved tool");
    }
  }

  private void requirePublicFailureCode(String code) {
    if (!LearningPlanDraftGenerationConstants.PUBLIC_FAILURE_CODES.contains(code)) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry has an invalid failure code");
    }
  }

  private void requireExactFields(JsonNode data, String... fields) {
    if (data.size() != fields.length) {
      throw new LearningPlanGenerationRealtimeUnavailableException(
          "Learning plan generation realtime Redis entry has unexpected fields");
    }
    for (String field : fields) {
      if (!data.hasNonNull(field) || (field.equals(LearningPlanGenerationRealtimeProtocol.DATA_DRAFT_ID)
          ? !data.get(field).canConvertToLong()
          : !data.get(field).isTextual())) {
        throw new LearningPlanGenerationRealtimeUnavailableException(
            "Learning plan generation realtime Redis entry is malformed");
      }
    }
  }

  private Duration retention(LearningPlanDraftGenerationEvent event) {
    return event instanceof LearningPlanDraftGenerationEvent.Completed
        || event instanceof LearningPlanDraftGenerationEvent.Failed
        ? properties.getCompletedRetention()
        : properties.getActiveRetention();
  }

  private Duration revisionRetention(LearningPlanDraftRevisionGenerationEvent event) {
    return event instanceof LearningPlanDraftRevisionGenerationEvent.Completed
        || event instanceof LearningPlanDraftRevisionGenerationEvent.Failed
        || event instanceof LearningPlanDraftRevisionGenerationEvent.Superseded
        ? properties.getCompletedRetention()
        : properties.getActiveRetention();
  }

  private void recordFailure(String resourceType, String operation, Throwable failure) {
    metrics.record(resourceType, operation, FAILURE);
    if (failure != null) {
      log.warn("Learning plan generation realtime Redis operation failed. operation={} exceptionType={}",
          operation, failure.getClass().getSimpleName());
    }
  }

  private static RedisURI redisUri(LearningPlanGenerationRealtimeStreamProperties properties) {
    RedisURI uri = RedisURI.create(properties.getHost(), properties.getPort());
    uri.setDatabase(properties.getDatabase());
    uri.setTimeout(properties.getCommandTimeout());
    uri.setSsl(properties.isSslEnabled());
    if (!properties.getUsername().isBlank()) {
      uri.setUsername(properties.getUsername());
    }
    if (!properties.getPassword().isBlank()) {
      uri.setPassword(properties.getPassword().toCharArray());
    }
    return uri;
  }
}
