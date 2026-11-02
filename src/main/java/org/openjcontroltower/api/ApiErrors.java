package org.openjcontroltower.api;

import org.openjcontroltower.application.PolicyGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,String>> badRequest(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("error",error.getMessage()==null?"Invalid request":error.getMessage()));
    }
    @ExceptionHandler(PolicyGuard.PolicyDeniedException.class)
    ResponseEntity<Map<String,String>> denied(PolicyGuard.PolicyDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error",error.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,String>> conflict(IllegalStateException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",error.getMessage()==null?"Conflict":error.getMessage()));
    }
}
