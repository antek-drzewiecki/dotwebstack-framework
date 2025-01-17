package org.dotwebstack.framework.core.scalars;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.schema.CoercingSerializeException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateCoercingTest {

  private final DateCoercing coercing = new DateCoercing();

  @Test
  void serialize_ReturnsDate_ForDate() {
    // Arrange
    LocalDate input = LocalDate.now();

    // Act
    LocalDate date = coercing.serialize(input);

    // Assert
    assertThat(date, is(sameInstance(input)));
  }

  @Test
  void serialize_ReturnsDate_ForValidDateString() {
    // Act
    LocalDate date = coercing.serialize("2018-05-30");

    // Assert
    assertThat(date, is(equalTo(LocalDate.of(2018, 5, 30))));
  }

  @Test
  void serialize_ThrowsException_ForInvalidDateString() {
    // Act / Assert
    assertThrows(CoercingSerializeException.class, () -> coercing.serialize("foo"));
  }

  @Test
  void serialize_ReturnsDate_ForOtherTypes() {
    // Act / Assert
    assertThrows(CoercingSerializeException.class, () -> coercing.serialize(123));
  }

  @Test
  void parseValue_ThrowsException() {
    // Act / Assert
    assertThrows(CoercingSerializeException.class, () -> coercing.parseValue(new Object()));
  }

  @Test
  void parseLiteral_ThrowsException() {
    // Act / Assert
    assertThrows(UnsupportedOperationException.class, () -> coercing.parseLiteral(new Object()));
  }

}
