package org.congcong.algomentor.agent.persistence.postgres.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(Map.class)
public class JsonbMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final ObjectMapper objectMapper;

  public JsonbMapTypeHandler() {
    this(new ObjectMapper());
  }

  public JsonbMapTypeHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void setNonNullParameter(
      PreparedStatement ps,
      int index,
      Map<String, Object> parameter,
      JdbcType jdbcType
  ) throws SQLException {
    PGobject jsonb = new PGobject();
    jsonb.setType("jsonb");
    jsonb.setValue(toJson(parameter));
    ps.setObject(index, jsonb);
  }

  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private String toJson(Map<String, Object> value) throws SQLException {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new SQLException("Failed to serialize PostgreSQL JSONB map", ex);
    }
  }

  private Map<String, Object> parse(String value) throws SQLException {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(value, MAP_TYPE);
    } catch (JsonProcessingException ex) {
      throw new SQLException("Failed to parse PostgreSQL JSONB map", ex);
    }
  }
}
