package org.congcong.algomentor.cache.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.Objects;
import org.congcong.algomentor.cache.config.CacheProperties;

/** Lettuce client 和共享连接的唯一持有者；连接在第一条命令时才创建。 */
public final class RedisConnectionManager implements AutoCloseable {

  private final CacheProperties.Redis properties;
  private final RedisClient client;
  private volatile StatefulRedisConnection<byte[], byte[]> connection;

  public RedisConnectionManager(CacheProperties.Redis properties) {
    this.properties = Objects.requireNonNull(properties, "redis properties must not be null");
    this.client = RedisClient.create(redisUri(properties));
    this.client.setOptions(ClientOptions.builder()
        .autoReconnect(true)
        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
        .socketOptions(SocketOptions.builder().connectTimeout(properties.getConnectTimeout()).build())
        .build());
  }

  public RedisCommands<byte[], byte[]> commands() {
    StatefulRedisConnection<byte[], byte[]> current = connection;
    if (current == null) {
      synchronized (this) {
        current = connection;
        if (current == null) {
          current = client.connect(ByteArrayCodec.INSTANCE);
          connection = current;
        }
      }
    }
    return current.sync();
  }

  @Override
  public synchronized void close() {
    StatefulRedisConnection<byte[], byte[]> current = connection;
    connection = null;
    if (current != null) {
      current.close();
    }
    Duration timeout = properties.getShutdownTimeout();
    client.shutdown(timeout, timeout);
  }

  private static RedisURI redisUri(CacheProperties.Redis properties) {
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
