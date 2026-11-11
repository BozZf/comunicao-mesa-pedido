package br.com.mesapedido.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
  public final HttpStatus status;

  public BusinessException(HttpStatus s, String message) {
    super(message);
    status = s;
  }
}
