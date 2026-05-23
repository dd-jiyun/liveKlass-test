package com.liveklass.domain.klass;

import com.liveklass.domain.BaseTimeEntity;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Getter
@Table(name = "klasses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Klass extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int maxCapacity;

    @Column(nullable = false)
    private int enrolledCount;

    @Embedded
    private KlassPeriod period;

    @Column(nullable = false)
    private int cancellationDeadlineDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KlassStatus status;

    public static Klass create(User creator, String title, String description, BigDecimal price,
                               int maxCapacity, LocalDate startDate, LocalDate endDate,
                               LocalDate enrollmentDeadline) {
        return create(creator, title, description, price, maxCapacity, startDate, endDate,
                enrollmentDeadline, EnrollmentPolicy.DEFAULT_CANCELLATION_DEADLINE_DAYS);
    }

    public static Klass create(User creator, String title, String description, BigDecimal price,
                               int maxCapacity, LocalDate startDate, LocalDate endDate,
                               LocalDate enrollmentDeadline, int cancellationDeadlineDays) {
        validatePrice(price);
        validateMaxCapacity(maxCapacity);
        validateCancellationDeadlineDays(cancellationDeadlineDays);
        Klass klass = new Klass();
        klass.creator = creator;
        klass.title = title;
        klass.description = description;
        klass.price = price;
        klass.maxCapacity = maxCapacity;
        klass.enrolledCount = 0;
        klass.period = KlassPeriod.create(startDate, endDate, enrollmentDeadline);
        klass.cancellationDeadlineDays = cancellationDeadlineDays;
        klass.status = KlassStatus.DRAFT;
        return klass;
    }

    public void open(LocalDateTime now) {
        if (period.hasEnrollmentDeadlinePassed(now.toLocalDate())) {
            throw new IllegalStateException("마감일이 이미 지나 OPEN 전환이 불가합니다.");
        }
        this.status = KlassStatus.OPEN;
    }

    public void close() {
        this.status = KlassStatus.CLOSED;
    }

    public void closeIfDeadlineReached(LocalDateTime now) {
        if (status == KlassStatus.OPEN && period.hasEnrollmentDeadlinePassed(now.toLocalDate())) {
            this.status = KlassStatus.CLOSED;
        }
    }

    public void extendDeadline(LocalDate newDeadline) {
        this.period = period.withExtendedDeadline(newDeadline);
    }

    public void validateEnrollable(LocalDateTime now) {
        if (status != KlassStatus.OPEN) {
            throw new IllegalStateException("신청 가능한 상태가 아닙니다. 현재 상태: " + status);
        }
        if (period.hasEnrollmentDeadlinePassed(now.toLocalDate())) {
            throw new IllegalStateException("신청 마감일이 지났습니다.");
        }
    }

    public void increaseEnrolledCount() {
        this.enrolledCount++;
    }

    public void decreaseEnrolledCount() {
        this.enrolledCount--;
    }

    public void changePrice(BigDecimal newPrice) {
        if (status != KlassStatus.DRAFT) {
            throw new IllegalStateException("수강료는 초안(DRAFT) 상태에서만 변경할 수 있습니다.");
        }
        this.price = newPrice;
    }

    public void changeMaxCapacity(int newMaxCapacity) {
        if (status != KlassStatus.DRAFT) {
            throw new IllegalStateException("최대 정원은 초안(DRAFT) 상태에서만 변경할 수 있습니다.");
        }
        this.maxCapacity = newMaxCapacity;
    }

    public boolean isFull() {
        return enrolledCount >= maxCapacity;
    }

    public boolean isFree() {
        return price.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isCancellable() {
        return cancellationDeadlineDays > 0;
    }

    public LocalDateTime cancellationDeadlineAt() {
        return period.getStartDate().minusDays(cancellationDeadlineDays).atTime(LocalTime.MAX);
    }

    private static void validatePrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }
    }

    private static void validateMaxCapacity(int maxCapacity) {
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("정원은 1 이상이어야 합니다.");
        }
    }

    private static void validateCancellationDeadlineDays(int cancellationDeadlineDays) {
        if (cancellationDeadlineDays < 0) {
            throw new IllegalArgumentException("취소 가능 기간은 0 이상이어야 합니다.");
        }
    }
}
