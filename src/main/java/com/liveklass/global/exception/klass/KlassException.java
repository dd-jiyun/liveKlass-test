package com.liveklass.global.exception.klass;

import com.liveklass.global.exception.DomainException;
import com.liveklass.global.exception.ErrorCode;

public class KlassException extends DomainException {

    public KlassException(ErrorCode errorCode) {
        super(errorCode);
    }

    public KlassException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
