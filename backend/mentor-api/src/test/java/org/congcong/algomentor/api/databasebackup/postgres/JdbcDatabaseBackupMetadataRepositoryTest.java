package org.congcong.algomentor.api.databasebackup.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JdbcDatabaseBackupMetadataRepositoryTest {

  @Test
  void verifiesTableAndSequencePrivilegesWithoutCallingSequenceFunctionsForTables() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement privilegeStatement = mock(PreparedStatement.class);
    ResultSet resultSet = mock(ResultSet.class);
    Statement replicationRoleStatement = mock(Statement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(privilegeStatement);
    when(privilegeStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBoolean(1)).thenReturn(true);
    when(resultSet.getBoolean(2)).thenReturn(true);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.createStatement()).thenReturn(replicationRoleStatement);

    new JdbcDatabaseBackupMetadataRepository(dataSource, "0.1.0-SNAPSHOT").verifyRestorePrivileges();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    assertThat(sql.getValue())
        .contains("FILTER (WHERE c.relkind IN ('r', 'p'))")
        .contains("FILTER (WHERE c.relkind = 'S')");
    verify(replicationRoleStatement).execute("SET LOCAL session_replication_role = replica");
    verify(replicationRoleStatement).execute("SET LOCAL session_replication_role = origin");
    verify(connection).rollback();
  }
}
