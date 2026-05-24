package com.liveklass.global.exception.enrollment;

import com.liveklass.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EnrollmentErrorCode implements ErrorCode {

    ENROLLMENT_NOT_FOUND("ENROLLMENT_NOT_FOUND", "수강 신청 내역을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ENROLLMENT_USER_NOT_FOUND("ENROLLMENT_USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ENROLLMENT_CAPACITY_EXCEEDED("ENROLLMENT_CAPACITY_EXCEEDED", "정원이 꽉 찼습니다. 대기 등록을 해보시겠습니까?", HttpStatus.CONFLICT),
    ENROLLMENT_DUPLICATE("ENROLLMENT_DUPLICATE", "이미 신청한 강의입니다.", HttpStatus.CONFLICT),
    ENROLLMENT_STATE_ERROR("ENROLLMENT_STATE_ERROR", "현재 상태에서는 처리할 수 없습니다.", HttpStatus.BAD_REQUEST),
    ENROLLMENT_WAITLIST_PRIORITY("ENROLLMENT_WAITLIST_PRIORITY", "알림을 받은 대기자가 있어 수강 신청할 수 없습니다.", HttpStatus.CONFLICT),
    ENROLLMENT_FORBIDDEN("ENROLLMENT_FORBIDDEN", "본인의 수강 신청만 처리할 수 있습니다.", HttpStatus.FORBIDDEN);

    private final String name;
    private final String message;
    private final HttpStatus httpStatus;

    EnrollmentErrorCode(String name, String message, HttpStatus httpStatus) {
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
