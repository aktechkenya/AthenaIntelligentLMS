package com.athena.lms.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
