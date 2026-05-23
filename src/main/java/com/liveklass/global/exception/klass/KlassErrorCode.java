package com.liveklass.global.exception.klass;

import com.liveklass.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KlassErrorCode implements ErrorCode {

    KLASS_NOT_FOUND("KLASS_NOT_FOUND", "강의를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    KLASS_USER_NOT_FOUND("KLASS_USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    KLASS_FORBIDDEN("KLASS_FORBIDDEN", "해당 강의에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    KLASS_STATE_ERROR("KLASS_STATE_ERROR", "현재 상태에서는 처리할 수 없습니다.", HttpStatus.BAD_REQUEST);

    private final String name;
    private final String message;
    private final HttpStatus httpStatus;

    KlassErrorCode(String name, String message, HttpStatus httpStatus) {
        this.name = name;
        this.message = message;
        this.httpStatus = httpStatus;
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
