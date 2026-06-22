package org.congcong.algomentor.agent.persistence.postgres.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(JsonNode.class)
public class JsonbTypeHandler extends BaseTypeHandler<JsonNode> {

  private final ObjectMapper objectMapper;

  public JsonbTypeHandler() {
    this(new ObjectMapper());
  }

  public JsonbTypeHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void setNonNullParameter(
      PreparedStatement ps,
      int index,
      JsonNode parameter,
      JdbcType jdbcType
  ) throws SQLException {
    PGobject jsonb = new PGobject();
    jsonb.setType("jsonb");
    jsonb.setValue(toJson(parameter));
    ps.setObject(index, jsonb);
  }

  @Override
  public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private String toJson(JsonNode value) throws SQLException {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new SQLException("Failed to serialize PostgreSQL JSONB value", ex);
    }
  }

  private JsonNode parse(String value) throws SQLException {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException ex) {
      throw new SQLException("Failed to parse PostgreSQL JSONB value", ex);
    }
  }
}
