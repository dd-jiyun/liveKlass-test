package com.liveklass.dto.enrollment;

import com.liveklass.domain.enrollment.Enrollment;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        Long id,
        Long klassId,
        String klassTitle,
        String status,
        LocalDateTime pendingExpiresAt,
        LocalDateTime confirmedAt,
        LocalDateTime enrolledAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getKlass().getId(),
                enrollment.getKlass().getTitle(),
                enrollment.getStatus().name(),
                enrollment.getPendingExpiresAt(),
                enrollment.getConfirmedAt(),
                enrollment.getCreatedAt()
        );
    }
}
