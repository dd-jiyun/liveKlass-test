package com.liveklass.domain.enrollment;

import com.liveklass.domain.BaseTimeEntity;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import jakarta.persistence.Column;
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

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "enrollments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "klass_id", nullable = false)
    private Klass klass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    private LocalDateTime pendingExpiresAt;
    private LocalDateTime confirmedAt;

    public static Enrollment create(User user, Klass klass, LocalDateTime now) {
        klass.validateEnrollable(now);
        klass.increaseEnrolledCount();
        Enrollment enrollment = new Enrollment();
        enrollment.user = user;
        enrollment.klass = klass;
        enrollment.status = EnrollmentStatus.PENDING;
        enrollment.pendingExpiresAt = now.plusMinutes(EnrollmentPolicy.PENDING_EXPIRE_MINUTES);
        return enrollment;
    }

    public static Enrollment createConfirmed(User user, Klass klass, LocalDateTime now) {
        klass.increaseEnrolledCount();
        Enrollment enrollment = new Enrollment();
        enrollment.user = user;
        enrollment.klass = klass;
        enrollment.status = EnrollmentStatus.CONFIRMED;
        enrollment.confirmedAt = now;
        return enrollment;
    }

    public void confirm(LocalDateTime now) {
        validateConfirmable(now);
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void expireIfOverdue(LocalDateTime now) {
        if (isPendingExpired(now)) {
            this.status = EnrollmentStatus.CANCELLED;
            klass.decreaseEnrolledCount();
        }
    }

    public void cancel(LocalDateTime now) {
        if (status == EnrollmentStatus.PENDING) {
            cancelPending();
            return;
        }
        if (status == EnrollmentStatus.CONFIRMED) {
            cancelConfirmed(now);
            return;
        }
        throw new IllegalStateException("취소할 수 없는 상태입니다: " + status);
    }

    public void autoConfirmIfFree(LocalDateTime now) {
        if (klass.isFree()) {
            this.status = EnrollmentStatus.CONFIRMED;
            this.confirmedAt = now;
        }
    }

    private void validateConfirmable(LocalDateTime now) {
        if (status != EnrollmentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 결제 확정이 가능합니다.");
        }
        if (now.isAfter(pendingExpiresAt)) {
            throw new IllegalStateException("임시 예약이 만료되어 결제 확정이 불가합니다.");
        }
    }

    private boolean isPendingExpired(LocalDateTime now) {
        return status == EnrollmentStatus.PENDING && now.isAfter(pendingExpiresAt);
    }

    private void cancelPending() {
        this.status = EnrollmentStatus.CANCELLED;
        klass.decreaseEnrolledCount();
    }

    private void cancelConfirmed(LocalDateTime now) {
        validateCancellationAllowed(now);
        this.status = EnrollmentStatus.CANCELLED;
        klass.decreaseEnrolledCount();
    }

    private void validateCancellationAllowed(LocalDateTime now) {
        if (!klass.isCancellable()) {
            throw new IllegalStateException("취소가 불가능한 강의입니다.");
        }
        if (now.isAfter(klass.cancellationDeadlineAt())) {
            throw new IllegalStateException("취소 가능 기간이 초과되었습니다.");
        }
    }
}
