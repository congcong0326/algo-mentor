package org.congcong.algomentor.cache.redis.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.codec.RedisValueCodecException;
import org.junit.jupiter.api.Test;

class JacksonRedisValueCodecFactoryTest {

  private final JacksonRedisValueCodecFactory factory = JacksonRedisValueCodecFactory.standalone();

  @Test
  void encodesJsonAndStrictBooleanScalars() {
    RedisValueCodec<Snapshot> json = factory.json(Snapshot.class);
    RedisValueCodec<Boolean> scalar = factory.booleanAsZeroOrOne();

    assertThat(json.decode(json.encode(new Snapshot("value")))).isEqualTo(new Snapshot("value"));
    assertThat(scalar.encode(true)).containsExactly((byte) '1');
    assertThat(scalar.decode(new byte[] {'0'})).isFalse();
  }

  @Test
  void rejectsInvalidRedisPayloads() {
    assertThatThrownBy(() -> factory.booleanAsZeroOrOne().decode(new byte[] {'2'}))
        .isInstanceOf(RedisValueCodecException.class);
    assertThatThrownBy(() -> factory.json(Snapshot.class).decode("not-json".getBytes()))
        .isInstanceOf(RedisValueCodecException.class);
    assertThatThrownBy(() -> factory.json(Snapshot.class).decode("{}".getBytes()))
        .isInstanceOf(RedisValueCodecException.class);
  }

  @Test
  void retainsNullEnvelopeValueWhenApplicationMapperOmitsNulls() {
    ObjectMapper applicationMapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    RedisValueCodec<Envelope> codec = new JacksonRedisValueCodecFactory(applicationMapper)
        .json(Envelope.class);

    assertThat(new String(codec.encode(new Envelope(false, null))))
        .isEqualTo("{\"present\":false,\"value\":null}");
    assertThat(codec.decode(codec.encode(new Envelope(false, null))))
        .isEqualTo(new Envelope(false, null));
  }

  record Snapshot(String value) { }

  record Envelope(boolean present, String value) { }
}
