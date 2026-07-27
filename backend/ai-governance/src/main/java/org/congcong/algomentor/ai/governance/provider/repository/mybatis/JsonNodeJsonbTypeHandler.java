package org.congcong.algomentor.ai.governance.provider.repository.mybatis;

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

/** provider 配置专用的 JSONB 映射，不向日志或异常暴露配置正文。 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(JsonNode.class)
public final class JsonNodeJsonbTypeHandler extends BaseTypeHandler<JsonNode> {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void setNonNullParameter(PreparedStatement statement, int index, JsonNode value, JdbcType jdbcType)
      throws SQLException {
    PGobject jsonb = new PGobject();
    jsonb.setType("jsonb");
    jsonb.setValue(write(value));
    statement.setObject(index, jsonb);
  }

  @Override
  public JsonNode getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
    return read(resultSet.getString(columnName));
  }

  @Override
  public JsonNode getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
    return read(resultSet.getString(columnIndex));
  }

  @Override
  public JsonNode getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
    return read(statement.getString(columnIndex));
  }

  private String write(JsonNode value) throws SQLException {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new SQLException("Failed to serialize provider config JSONB", exception);
    }
  }

  private JsonNode read(String value) throws SQLException {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new SQLException("Failed to parse provider config JSONB", exception);
    }
  }
}
