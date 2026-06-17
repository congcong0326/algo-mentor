package org.congcong.algomentor.agent.persistence.postgres.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class JsonbTypeHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void bindsJsonNodeAsPostgresJsonbObject() throws Exception {
    JsonbTypeHandler handler = new JsonbTypeHandler(objectMapper);
    PreparedStatementProbe probe = new PreparedStatementProbe();

    handler.setNonNullParameter(probe.preparedStatement(), 1, objectMapper.readTree("{\"role\":\"assistant\"}"), null);

    assertThat(probe.index).isEqualTo(1);
    assertThat(probe.value).isInstanceOf(PGobject.class);
    PGobject jsonb = (PGobject) probe.value;
    assertThat(jsonb.getType()).isEqualTo("jsonb");
    assertThat(jsonb.getValue()).isEqualTo("{\"role\":\"assistant\"}");
  }

  @Test
  void readsJsonNodeFromPostgresJsonbText() throws Exception {
    JsonbTypeHandler handler = new JsonbTypeHandler(objectMapper);

    JsonNode value = handler.getNullableResult(resultSet("payload", "{\"createdAt\":\"2026-01-01T00:00:00Z\"}"), "payload");

    assertThat(value.get("createdAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
  }

  @Test
  void mapsLowercaseDatabaseRoleToUppercaseCoreEnum() throws Exception {
    AgentMessageRoleTypeHandler handler = new AgentMessageRoleTypeHandler();

    AgentMessage.Role role = handler.getNullableResult(resultSet("role", "user"), "role");

    assertThat(role).isEqualTo(AgentMessage.Role.USER);
  }

  private ResultSet resultSet(String column, String value) {
    return (ResultSet) Proxy.newProxyInstance(
        ResultSet.class.getClassLoader(),
        new Class<?>[] {ResultSet.class},
        (proxy, method, args) -> {
          if ("getString".equals(method.getName()) && args.length == 1 && column.equals(args[0])) {
            return value;
          }
          throw new UnsupportedOperationException("Unsupported ResultSet method: " + method.getName());
        });
  }

  private static final class PreparedStatementProbe {
    private int index;
    private Object value;

    private PreparedStatement preparedStatement() {
      return (PreparedStatement) Proxy.newProxyInstance(
          PreparedStatement.class.getClassLoader(),
          new Class<?>[] {PreparedStatement.class},
          (proxy, method, args) -> {
            if ("setObject".equals(method.getName())) {
              index = (Integer) args[0];
              value = args[1];
              return null;
            }
            throw new UnsupportedOperationException("Unsupported PreparedStatement method: " + method.getName());
          });
    }
  }
}
