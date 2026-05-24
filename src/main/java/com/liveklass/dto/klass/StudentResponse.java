package com.liveklass.dto.klass;

import com.liveklass.domain.enrollment.Enrollment;

import java.time.LocalDateTime;

public record StudentResponse(
        Long enrollmentId,
        Long userId,
        String userName,
        LocalDateTime confirmedAt
) {
    public static StudentResponse from(Enrollment enrollment) {
        return new StudentResponse(
                enrollment.getId(),
                enrollment.getUser().getId(),
                enrollment.getUser().getName(),
                enrollment.getConfirmedAt()
        );
    }
}
