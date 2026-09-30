package com.collabo.backend.yarn;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Turns YarnException into { "message": ... } with its own status. Scoped to this component's controller only. */
@RestControllerAdvice(assignableTypes = YarnController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class YarnErrors {

    @ExceptionHandler(YarnException.class)
    ResponseEntity<Map<String, String>> handle(YarnException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}
