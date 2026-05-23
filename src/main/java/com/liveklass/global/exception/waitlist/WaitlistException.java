package com.liveklass.global.exception.waitlist;

import com.liveklass.global.exception.DomainException;
import com.liveklass.global.exception.ErrorCode;

public class WaitlistException extends DomainException {

    public WaitlistException(ErrorCode errorCode) {
        super(errorCode);
    }

    public WaitlistException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
