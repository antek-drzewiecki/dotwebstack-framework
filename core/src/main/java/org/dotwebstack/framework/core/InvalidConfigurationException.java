package org.dotwebstack.framework.core;

import lombok.NonNull;

public class InvalidConfigurationException extends DotWebStackRuntimeException {

  private static final long serialVersionUID = 7760665084527035669L;

  public InvalidConfigurationException(@NonNull String message, Object... arguments) {
    super(message, arguments);
  }

  public InvalidConfigurationException(
      @NonNull String message, Throwable cause, Object... arguments) {
    super(message, cause, arguments);
  }
}
