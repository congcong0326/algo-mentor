package org.congcong.algomentor.policy.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.congcong.algomentor.policy.service.GenericPolicyException;
import org.junit.jupiter.api.Test;

class GenericPolicyTypeRegistryTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void normalizesCodeAndDeserializesGenericContentType() throws Exception {
    GenericPolicyType<List<Rule>> policyType = GenericPolicyType.of(
        "  DEMO.RULES  ", new TypeReference<List<Rule>>() {});
    GenericPolicyTypeRegistry registry = new GenericPolicyTypeRegistry(List.of(policyType));

    assertThat(registry.require("demo.rules")).isSameAs(policyType);
    assertThat(policyType.deserialize(objectMapper, objectMapper.readTree("[{\"limit\":3}]")))
        .containsExactly(new Rule(3));
  }

  @Test
  void rejectsDuplicateTypeCodeRegistrations() {
    GenericPolicyType<String> first = GenericPolicyType.of("demo", String.class);
    GenericPolicyType<String> duplicate = GenericPolicyType.of("DEMO", String.class);

    assertThatThrownBy(() -> new GenericPolicyTypeRegistry(List.of(first, duplicate)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate GenericPolicyType registration");
  }

  @Test
  void rejectsUnregisteredAndDetachedDescriptors() {
    GenericPolicyType<String> registered = GenericPolicyType.of("demo", String.class);
    GenericPolicyType<String> detached = GenericPolicyType.of("demo", String.class);
    GenericPolicyTypeRegistry registry = new GenericPolicyTypeRegistry(List.of(registered));

    assertThatThrownBy(() -> registry.require("missing"))
        .isInstanceOf(GenericPolicyException.class);
    assertThatThrownBy(() -> registry.requireRegisteredInstance(detached))
        .isInstanceOf(GenericPolicyException.class);
  }

  private record Rule(int limit) {
  }
}
