package com.eCommerce.couponApiMvc.handler;

import com.eCommerce.couponApiMvc.dto.CouponIssueResponseDto;
import com.eCommerce.couponDomain.exception.CouponIssueException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CouponIssueException.class)
    public ResponseEntity<CouponIssueResponseDto> handleCouponIssue(CouponIssueException e) {
        return ResponseEntity.badRequest()
                .body(new CouponIssueResponseDto(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CouponIssueResponseDto> handleRuntime(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(new CouponIssueResponseDto("FAIL", e.getMessage()));
    }
}
