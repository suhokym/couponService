package com.eCommerce.couponAdmin.exception;

import com.eCommerce.couponDomain.exception.CouponIssueException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 쿠폰 도메인 비즈니스 예외 처리
     * - 존재하지 않는 캠페인, 발급 요청 등 도메인 규칙 위반 시 400 반환
     */
    @ExceptionHandler(CouponIssueException.class)
    public ResponseEntity<Map<String, String>> handleCouponIssueException(CouponIssueException e) {
        log.warn("CouponIssueException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "errorCode", e.getErrorCode().name(),
                        "message", e.getMessage()
                ));
    }

    /**
     * 그 외 예상치 못한 서버 오류 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 내부 오류가 발생했습니다."));
    }
}
