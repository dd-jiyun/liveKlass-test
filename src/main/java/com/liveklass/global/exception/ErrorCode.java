package com.liveklass.global.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

    String getName();

    String getMessage();

    HttpStatus getHttpStatus();
}
