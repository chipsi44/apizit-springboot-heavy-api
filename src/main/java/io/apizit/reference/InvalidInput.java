package io.apizit.reference;

public class InvalidInput extends RuntimeException {
  private final int status;

  public InvalidInput(String message) {
    this(message, 400);
  }

  public InvalidInput(String message, int status) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
