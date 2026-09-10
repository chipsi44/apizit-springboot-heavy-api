package io.apizit.reference;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(InvalidInput.class)
  ResponseEntity<Map<String, String>> invalid(InvalidInput error) {
    return ResponseEntity.status(error.status()).body(Map.of("error", error.getMessage()));
  }
}
