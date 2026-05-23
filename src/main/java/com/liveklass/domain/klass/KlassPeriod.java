package com.liveklass.domain.klass;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KlassPeriod {

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalDate enrollmentDeadline;

    public static KlassPeriod create(LocalDate startDate, LocalDate endDate, LocalDate enrollmentDeadline) {
        validateEndDateAfterStartDate(startDate, endDate);
        validateEnrollmentDeadlineBeforeStartDate(enrollmentDeadline, startDate);
        KlassPeriod period = new KlassPeriod();
        period.startDate = startDate;
        period.endDate = endDate;
        period.enrollmentDeadline = enrollmentDeadline;
        return period;
    }

    public boolean hasEnrollmentDeadlinePassed(LocalDate today) {
        return !enrollmentDeadline.isAfter(today);
    }

    public KlassPeriod withExtendedDeadline(LocalDate newDeadline) {
        return KlassPeriod.create(startDate, endDate, newDeadline);
    }

    private static void validateEndDateAfterStartDate(LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일 이후여야 합니다.");
        }
    }

    private static void validateEnrollmentDeadlineBeforeStartDate(LocalDate enrollmentDeadline, LocalDate startDate) {
        if (!enrollmentDeadline.isBefore(startDate)) {
            throw new IllegalArgumentException("마감일은 수강 시작일 이전이어야 합니다.");
        }
    }
}
