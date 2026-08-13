package org.congcong.algomentor.cache.redis.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.codec.RedisValueCodecException;
import org.congcong.algomentor.cache.codec.RedisValueCodecFactory;

/** 使用明确 value class 的紧凑 UTF-8 JSON Redis codec。 */
public final class JacksonRedisValueCodecFactory implements RedisValueCodecFactory {

  private final ObjectMapper objectMapper;

  public JacksonRedisValueCodecFactory(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
        .copy()
        .registerModule(new JavaTimeModule())
        .setSerializationInclusion(JsonInclude.Include.ALWAYS)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .deactivateDefaultTyping();
  }

  public static JacksonRedisValueCodecFactory standalone() {
    return new JacksonRedisValueCodecFactory(new ObjectMapper());
  }

  @Override
  public <V> RedisValueCodec<V> json(Class<V> valueType) {
    Objects.requireNonNull(valueType, "valueType must not be null");
    return new RedisValueCodec<>() {
      @Override
      public byte[] encode(V value) {
        try {
          byte[] encoded = objectMapper.writeValueAsBytes(Objects.requireNonNull(value, "value must not be null"));
          return requireNonEmpty(encoded);
        } catch (RedisValueCodecException exception) {
          throw exception;
        } catch (Exception exception) {
          throw new RedisValueCodecException("Unable to encode Redis JSON value", exception);
        }
      }

      @Override
      public V decode(byte[] bytes) {
        try {
          return Objects.requireNonNull(objectMapper.readValue(requireNonEmpty(bytes), valueType),
              "decoded value must not be null");
        } catch (RedisValueCodecException exception) {
          throw exception;
        } catch (Exception exception) {
          throw new RedisValueCodecException("Unable to decode Redis JSON value", exception);
        }
      }
    };
  }

  @Override
  public RedisValueCodec<Boolean> booleanAsZeroOrOne() {
    return new RedisValueCodec<>() {
      @Override
      public byte[] encode(Boolean value) {
        if (value == null) {
          throw new RedisValueCodecException("Redis boolean value must not be null");
        }
        return new byte[] {(byte) (value ? '1' : '0')};
      }

      @Override
      public Boolean decode(byte[] bytes) {
        bytes = requireNonEmpty(bytes);
        if (bytes.length != 1 || (bytes[0] != '0' && bytes[0] != '1')) {
          throw new RedisValueCodecException("Redis boolean value must be ASCII 0 or 1");
        }
        return bytes[0] == '1';
      }
    };
  }

  private static byte[] requireNonEmpty(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new RedisValueCodecException("Redis value bytes must not be empty");
    }
    return bytes;
  }
}
