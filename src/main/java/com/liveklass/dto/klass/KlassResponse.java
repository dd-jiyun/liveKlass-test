package com.liveklass.dto.klass;

import com.liveklass.domain.klass.Klass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record KlassResponse(
        Long id,
        String title,
        String description,
        String status,
        BigDecimal price,
        int maxCapacity,
        int enrolledCount,
        boolean isFull,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate enrollmentDeadline,
        int cancellationDeadlineDays,
        Long creatorId,
        LocalDateTime createdAt
) {
    public static KlassResponse from(Klass klass) {
        return new KlassResponse(
                klass.getId(),
                klass.getTitle(),
                klass.getDescription(),
                klass.getStatus().name(),
                klass.getPrice(),
                klass.getMaxCapacity(),
                klass.getEnrolledCount(),
                klass.isFull(),
                klass.getPeriod().getStartDate(),
                klass.getPeriod().getEndDate(),
                klass.getPeriod().getEnrollmentDeadline(),
                klass.getCancellationDeadlineDays(),
                klass.getCreator().getId(),
                klass.getCreatedAt()
        );
    }
}
