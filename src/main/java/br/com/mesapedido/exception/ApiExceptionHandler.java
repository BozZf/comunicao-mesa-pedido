package br.com.mesapedido.exception;

import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                "Confira os campos enviados.",
                "errors",
                e.getBindingResult().getFieldErrors().stream()
                    .map(x -> x.getField() + ": " + x.getDefaultMessage())
                    .toList()));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("message", "Requisição inválida."));
  }

  @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("message", "Operação concorrente. Atualize e tente novamente."));
  }

  @ExceptionHandler(RestClientException.class)
  ResponseEntity<?> gateway(Exception e) {
    return ResponseEntity.status(502)
        .body(Map.of("message", "Provedor de pagamento indisponível. Tente novamente."));
  }
}
