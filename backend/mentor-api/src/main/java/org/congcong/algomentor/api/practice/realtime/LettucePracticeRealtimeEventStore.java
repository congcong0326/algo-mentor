package org.congcong.algomentor.api.practice.realtime;

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
import io.lettuce.core.event.Event;
import io.lettuce.core.event.connection.ConnectedEvent;
import io.lettuce.core.event.connection.ReconnectAttemptEvent;
import io.lettuce.core.event.connection.ReconnectFailedEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.api.config.PracticeRealtimeStreamProperties;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.PracticeRealtimeOperation;
import org.congcong.algomentor.ops.observability.PracticeRealtimeOpsRecorder;
import org.congcong.algomentor.ops.observability.PracticeRealtimeOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/** Lettuce 实现：写入异步提交，阻塞 XREAD 为每次读取单独创建连接。 */
public final class LettucePracticeRealtimeEventStore implements PracticeRealtimeEventStore, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(LettucePracticeRealtimeEventStore.class);
  private static final String STREAM_FIELD_ENVELOPE = "envelope";

  private final PracticeRealtimeStreamProperties properties;
  private final ObjectMapper objectMapper;
  private final PracticeRealtimeEventPayloadMapper payloadMapper;
  private final RedisClient client;
  private final PracticeRealtimeOpsRecorder opsRecorder;
  private final CompletableFuture<StatefulRedisConnection<String, String>> writeConnection;
  private final Semaphore readConnections;
  private final Disposable connectionEventSubscription;
  private final AtomicLong reconnectStartedAt = new AtomicLong();
  private final ConcurrentMap<String, AtomicLong> nextSequences = new ConcurrentHashMap<>();

  public LettucePracticeRealtimeEventStore(
      PracticeRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      PracticeRealtimeEventPayloadMapper payloadMapper
  ) {
    this(properties, objectMapper, payloadMapper, NoopOpsRecorders.practiceRealtime());
  }

  public LettucePracticeRealtimeEventStore(
      PracticeRealtimeStreamProperties properties,
      ObjectMapper objectMapper,
      PracticeRealtimeEventPayloadMapper payloadMapper,
      PracticeRealtimeOpsRecorder opsRecorder
  ) {
    this.properties = Objects.requireNonNull(properties, "Practice realtime properties must not be null");
    this.properties.validate();
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    this.payloadMapper = Objects.requireNonNull(payloadMapper, "Practice realtime payload mapper must not be null");
    this.opsRecorder = Objects.requireNonNull(opsRecorder, "Practice realtime ops recorder must not be null");
    this.client = RedisClient.create(redisUri(properties));
    this.client.setOptions(ClientOptions.builder()
        .autoReconnect(true)
        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
        .socketOptions(SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build())
        .build());
    this.readConnections = new Semaphore(properties.getMaxReadConnections(), true);
    this.connectionEventSubscription = client.getResources().eventBus().get().subscribe(this::onConnectionEvent);
    this.writeConnection = openWriteConnection();
  }

  @Override
  public void append(String runUuid, AgentStreamEvent event) {
    PracticeRealtimeEventPayload publicEvent = payloadMapper.map(event).orElse(null);
    if (publicEvent == null) {
      return;
    }
    String key = PracticeRealtimeProtocol.streamKey(runUuid);
    long appendStartedAt = System.nanoTime();
    long sequence = nextSequences.computeIfAbsent(runUuid, ignored -> new AtomicLong(1L)).getAndIncrement();
    String entryId = streamEntryId(sequence);
    try {
      String serialized = envelope(publicEvent);
      queueAppend(key, entryId, publicEvent.eventName(), serialized, retention(event), appendStartedAt);
    } catch (RuntimeException failure) {
      recordFailure(PracticeRealtimeOperation.APPEND, appendStartedAt, failure);
      recordPublicAppend(publicEvent.eventName(), 0, PracticeRealtimeOutcome.FAILURE);
    }
  }

  @Override
  public List<PracticeRealtimeEvent> readAfter(String runUuid, String after, boolean block) {
    String key = PracticeRealtimeProtocol.streamKey(runUuid);
    String cursor = PracticeRealtimeCursor.xreadOffset(after);
    long startedAt = System.nanoTime();
    long connectStartedAt = System.nanoTime();
    boolean connectionAttempted = false;
    boolean connected = false;
    boolean readConnectionAcquired = false;
    try {
      if (!readConnections.tryAcquire()) {
        PracticeRealtimeUnavailableException failure = new PracticeRealtimeUnavailableException(
            "Practice realtime Redis read connection capacity is exhausted");
        throw failure;
      }
      readConnectionAcquired = true;
      connectionAttempted = true;
      try (StatefulRedisConnection<String, String> connection = client.connect()) {
        record(PracticeRealtimeOperation.CONNECT, PracticeRealtimeOutcome.SUCCESS, connectStartedAt);
        connected = true;
        RedisCommands<String, String> commands = connection.sync();
        List<StreamMessage<String, String>> entries = block
            ? commands.xread(new XReadArgs().block(properties.getReadBlock()), XReadArgs.StreamOffset.from(key, cursor))
            : commands.xread(XReadArgs.StreamOffset.from(key, cursor));
        List<PracticeRealtimeEvent> events = entries == null ? List.of() : entries.stream().map(this::decode).toList();
        record(PracticeRealtimeOperation.READ, PracticeRealtimeOutcome.SUCCESS, startedAt);
        return events;
      }
    } catch (RuntimeException failure) {
      if (connectionAttempted && !connected) {
        recordFailure(PracticeRealtimeOperation.CONNECT, connectStartedAt, failure);
      }
      record(PracticeRealtimeOperation.READ, PracticeRealtimeOutcome.FAILURE, startedAt);
      log.warn("Practice realtime Redis operation failed. operation={} exceptionType={}",
          PracticeRealtimeOperation.READ.tagValue(), failure.getClass().getSimpleName());
      throw new PracticeRealtimeUnavailableException("Practice realtime Redis read failed", failure);
    } finally {
      if (readConnectionAcquired) {
        readConnections.release();
      }
    }
  }

  @Override
  public void close() {
    try {
      writeConnection.thenAccept(StatefulRedisConnection::close);
    } finally {
      connectionEventSubscription.dispose();
      client.shutdown(properties.getShutdownTimeout(), properties.getShutdownTimeout());
    }
  }

  private String envelope(PracticeRealtimeEventPayload event) {
    try {
      return objectMapper.writeValueAsString(Map.of(
          PracticeRealtimeProtocol.ENVELOPE_VERSION_FIELD, PracticeRealtimeProtocol.ENVELOPE_VERSION,
          PracticeRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD, event.eventName(),
          PracticeRealtimeProtocol.ENVELOPE_DATA_FIELD, event.data()));
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("Practice realtime event envelope serialization failed", failure);
    }
  }

  /**
   * Agent worker 只在独立连接已经可用时提交异步命令，绝不等待建连、XADD 或 EXPIRE 响应。
   * 若连接尚未就绪就直接丢弃实时事件并记为失败：Redis Stream 是尽力而为日志，不能在连接故障时
   * 通过 CompletableFuture 链累积事件，演变为内存队列或反压来源。
   *
   * <p>同一 run 仅由唯一事件出口在单个 worker 线程依次调用本方法；Lettuce 单连接会按命令提交顺序
   * 写出 XADD/EXPIRE，因此正常连接下该 run 的 entry 顺序仍与 Agent 事件顺序一致。</p>
   */
  private void queueAppend(
      String key,
      String entryId,
      String eventName,
      String serialized,
      Duration retention,
      long appendStartedAt
  ) {
    StatefulRedisConnection<String, String> connection = writeConnection.getNow(null);
    if (connection == null) {
      recordFailure(PracticeRealtimeOperation.APPEND, appendStartedAt,
          new PracticeRealtimeUnavailableException("Practice realtime Redis write connection is unavailable"));
      recordPublicAppend(eventName, serializedBytes(serialized), PracticeRealtimeOutcome.FAILURE);
      return;
    }
    appendAsync(connection, key, entryId, eventName, serialized, retention, appendStartedAt);
  }

  /** 同包集成测试用于在写入前等待独立异步连接完成。 */
  boolean writeConnectionReady() {
    return writeConnection.isDone() && !writeConnection.isCompletedExceptionally() && !writeConnection.isCancelled();
  }

  private CompletableFuture<StatefulRedisConnection<String, String>> openWriteConnection() {
    long connectStartedAt = System.nanoTime();
    CompletableFuture<StatefulRedisConnection<String, String>> connection =
        client.connectAsync(StringCodec.UTF8, redisUri(properties)).toCompletableFuture();
    connection.whenComplete((ignored, failure) -> {
      if (failure == null) {
        record(PracticeRealtimeOperation.CONNECT, PracticeRealtimeOutcome.SUCCESS, connectStartedAt);
      } else {
        recordFailure(PracticeRealtimeOperation.CONNECT, connectStartedAt, failure);
      }
    });
    return connection;
  }

  private void appendAsync(
      StatefulRedisConnection<String, String> connection,
      String key,
      String entryId,
      String eventName,
      String serialized,
      Duration retention,
      long appendStartedAt
  ) {
    RedisAsyncCommands<String, String> commands = connection.async();
    try {
      RedisFuture<String> append = commands.xadd(
          key,
          new XAddArgs().id(entryId),
          Map.of(STREAM_FIELD_ENVELOPE, serialized));
      append.whenComplete((ignored, failure) -> {
        if (failure == null) {
          record(PracticeRealtimeOperation.APPEND, PracticeRealtimeOutcome.SUCCESS, appendStartedAt);
          recordPublicAppend(eventName, serializedBytes(serialized), PracticeRealtimeOutcome.SUCCESS);
        } else {
          recordFailure(PracticeRealtimeOperation.APPEND, appendStartedAt, failure);
          recordPublicAppend(eventName, serializedBytes(serialized), PracticeRealtimeOutcome.FAILURE);
        }
      });
    } catch (RuntimeException failure) {
      recordFailure(PracticeRealtimeOperation.APPEND, appendStartedAt, failure);
      recordPublicAppend(eventName, serializedBytes(serialized), PracticeRealtimeOutcome.FAILURE);
    }

    long expireStartedAt = System.nanoTime();
    try {
      RedisFuture<Boolean> expire = commands.expire(key, retention);
      expire.whenComplete((expirationApplied, failure) -> {
        if (failure == null && Boolean.TRUE.equals(expirationApplied)) {
          record(PracticeRealtimeOperation.EXPIRE, PracticeRealtimeOutcome.SUCCESS, expireStartedAt);
        } else {
          Throwable expireFailure = failure == null
              ? new PracticeRealtimeUnavailableException("Practice realtime Redis stream expiration was not applied")
              : failure;
          recordFailure(PracticeRealtimeOperation.EXPIRE, expireStartedAt, expireFailure);
        }
      });
    } catch (RuntimeException failure) {
      recordFailure(PracticeRealtimeOperation.EXPIRE, expireStartedAt, failure);
    }
  }

  private PracticeRealtimeEvent decode(StreamMessage<String, String> entry) {
    String serialized = entry.getBody().get(STREAM_FIELD_ENVELOPE);
    if (serialized == null || serialized.isBlank()) {
      throw new PracticeRealtimeUnavailableException("Practice realtime Redis entry is malformed");
    }
    try {
      JsonNode envelope = objectMapper.readTree(serialized);
      if (envelope.path(PracticeRealtimeProtocol.ENVELOPE_VERSION_FIELD).asInt() != PracticeRealtimeProtocol.ENVELOPE_VERSION) {
        throw new PracticeRealtimeUnavailableException("Practice realtime Redis entry version is unsupported");
      }
      JsonNode eventName = envelope.get(PracticeRealtimeProtocol.ENVELOPE_EVENT_NAME_FIELD);
      JsonNode data = envelope.get(PracticeRealtimeProtocol.ENVELOPE_DATA_FIELD);
      if (eventName == null || !eventName.isTextual() || data == null) {
        throw new PracticeRealtimeUnavailableException("Practice realtime Redis entry is malformed");
      }
      return new PracticeRealtimeEvent(entry.getId(), eventName.asText(), data);
    } catch (JsonProcessingException failure) {
      throw new PracticeRealtimeUnavailableException("Practice realtime Redis entry cannot be decoded", failure);
    }
  }

  private static boolean isTerminal(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd || event instanceof AgentStreamEvent.AgentError;
  }

  private Duration retention(AgentStreamEvent event) {
    return isTerminal(event) ? properties.getCompletedRetention() : properties.getActiveRetention();
  }

  private void record(PracticeRealtimeOperation operation, PracticeRealtimeOutcome outcome, long startedAt) {
    opsRecorder.redisOperation(operation, outcome, Duration.ofNanos(System.nanoTime() - startedAt));
  }

  private void recordFailure(PracticeRealtimeOperation operation, long startedAt, Throwable failure) {
    record(operation, PracticeRealtimeOutcome.FAILURE, startedAt);
    log.warn("Practice realtime Redis operation failed. operation={} exceptionType={}",
        operation.tagValue(), failure.getClass().getSimpleName());
  }

  private void recordPublicAppend(String eventName, int payloadBytes, PracticeRealtimeOutcome outcome) {
    opsRecorder.publicEventAppend(eventName, payloadBytes, outcome);
  }

  private int serializedBytes(String serialized) {
    return serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  private String streamEntryId(long sequence) {
    return sequence + "-" + PracticeRealtimeProtocol.STREAM_ID_GENERATION;
  }

  /**
   * Lettuce 会把自动重连作为 Client 级事件发布。只记录连接状态和耗时，不关联 run、key 或事件正文，
   * 避免实时通道的诊断日志泄露用户内容。
   */
  private void onConnectionEvent(Event event) {
    if (event instanceof ReconnectAttemptEvent attempt) {
      reconnectStartedAt.compareAndSet(0L, System.nanoTime());
      log.info("Practice realtime Redis reconnect started. attempt={}", attempt.getAttempt());
      return;
    }
    if (event instanceof ReconnectFailedEvent failure) {
      long startedAt = reconnectStartedAt.getAndSet(0L);
      Throwable cause = failure.getCause() == null
          ? new PracticeRealtimeUnavailableException("Practice realtime Redis reconnect failed")
          : failure.getCause();
      recordFailure(
          PracticeRealtimeOperation.RECONNECT,
          startedAt == 0L ? System.nanoTime() : startedAt,
          cause);
      return;
    }
    if (event instanceof ConnectedEvent && reconnectStartedAt.get() != 0L) {
      long startedAt = reconnectStartedAt.getAndSet(0L);
      record(PracticeRealtimeOperation.RECONNECT, PracticeRealtimeOutcome.SUCCESS, startedAt);
      log.info("Practice realtime Redis reconnect succeeded.");
    }
  }

  private static RedisURI redisUri(PracticeRealtimeStreamProperties properties) {
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
