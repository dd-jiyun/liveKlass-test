package com.liveklass.global.exception.waitlist;

import com.liveklass.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WaitlistErrorCode implements ErrorCode {

    WAITLIST_NOT_FOUND("WAITLIST_NOT_FOUND", "대기 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WAITLIST_USER_NOT_FOUND("WAITLIST_USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WAITLIST_EMPTY("WAITLIST_EMPTY", "대기 중인 인원이 없습니다.", HttpStatus.NOT_FOUND),
    WAITLIST_DUPLICATE("WAITLIST_DUPLICATE", "이미 대기 중인 강의입니다.", HttpStatus.CONFLICT),
    WAITLIST_NOT_FULL("WAITLIST_NOT_FULL", "정원이 남아 있는 강의에는 대기 등록을 할 수 없습니다.", HttpStatus.BAD_REQUEST),
    WAITLIST_FORBIDDEN("WAITLIST_FORBIDDEN", "본인의 대기 항목만 처리할 수 있습니다.", HttpStatus.FORBIDDEN),
    WAITLIST_STATE_ERROR("WAITLIST_STATE_ERROR", "현재 상태에서는 처리할 수 없습니다.", HttpStatus.BAD_REQUEST);

    private final String name;
    private final String message;
    private final HttpStatus httpStatus;

    WaitlistErrorCode(String name, String message, HttpStatus httpStatus) {
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
