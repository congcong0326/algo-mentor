package org.congcong.algomentor.cache.coherence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcSharedCacheInvalidationEventStoreTest {

  @Test
  void bindsCleanupCutoffAsTimestampWithTimeZone() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    Instant cutoff = Instant.parse("2026-07-22T06:08:36.992Z");
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeUpdate()).thenReturn(3);
    JdbcSharedCacheInvalidationEventStore eventStore =
        new JdbcSharedCacheInvalidationEventStore(new JdbcTemplate(dataSource));

    int deleted = eventStore.deleteCreatedBefore(cutoff);

    assertThat(deleted).isEqualTo(3);
    verify(statement).setObject(
        1, cutoff.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
  }
}
