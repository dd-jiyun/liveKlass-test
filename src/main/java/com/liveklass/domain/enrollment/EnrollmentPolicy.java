package com.liveklass.domain.enrollment;

public final class EnrollmentPolicy {

    public static final int PENDING_EXPIRE_MINUTES = 20;
    public static final int NOTIFIED_ACCEPT_MINUTES = 20;
    public static final int DEFAULT_CANCELLATION_DEADLINE_DAYS = 7;

    private EnrollmentPolicy() {
    }
}
