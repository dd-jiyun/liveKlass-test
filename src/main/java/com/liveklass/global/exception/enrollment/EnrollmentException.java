package com.liveklass.global.exception.enrollment;

import com.liveklass.global.exception.DomainException;
import com.liveklass.global.exception.ErrorCode;

public class EnrollmentException extends DomainException {

    public EnrollmentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public EnrollmentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
