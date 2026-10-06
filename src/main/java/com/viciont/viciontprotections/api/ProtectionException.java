package com.viciont.viciontprotections.api;

/** Error de dominio con mensaje seguro para el chat, sin consultas SQL ni credenciales. */
public class ProtectionException extends RuntimeException {
  public ProtectionException(String message) {
    super(message);
  }

  public ProtectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
