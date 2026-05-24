package com.liveklass.domain.waitlist;

import com.liveklass.domain.BaseTimeEntity;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
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
@Table(name = "waitlists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "klass_id", nullable = false)
    private Klass klass;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status;

    private LocalDateTime notifiedAt;

    public static Waitlist create(User user, Klass klass, int nextPosition) {
        validateKlassIsOpen(klass);
        Waitlist waitlist = new Waitlist();
        waitlist.user = user;
        waitlist.klass = klass;
        waitlist.position = nextPosition;
        waitlist.status = WaitlistStatus.WAITING;
        return waitlist;
    }

    public void markAsNotified(LocalDateTime now) {
        validateNotifiable();
        this.status = WaitlistStatus.NOTIFIED;
        this.notifiedAt = now;
    }

    public void convert(LocalDateTime now) {
        validateConvertible(now);
        this.status = WaitlistStatus.CONVERTED;
    }

    public void expireIfOverdue(LocalDateTime now) {
        if (isNotifiedExpired(now)) {
            this.status = WaitlistStatus.EXPIRED;
        }
    }

    public void cancel() {
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.NOTIFIED) {
            throw new IllegalStateException("취소할 수 없는 대기 상태입니다: " + status);
        }
        this.status = WaitlistStatus.CANCELLED;
    }

    private static void validateKlassIsOpen(Klass klass) {
        if (klass.getStatus() != KlassStatus.OPEN) {
            throw new IllegalStateException("OPEN 상태의 강의에만 대기 등록할 수 있습니다.");
        }
    }

    private void validateNotifiable() {
        if (status != WaitlistStatus.WAITING) {
            throw new IllegalStateException("WAITING 상태에서만 NOTIFIED 전환이 가능합니다.");
        }
    }

    private void validateConvertible(LocalDateTime now) {
        if (status != WaitlistStatus.NOTIFIED) {
            throw new IllegalStateException("NOTIFIED 상태에서만 수강 신청으로 전환할 수 있습니다.");
        }
        if (now.isAfter(notifiedAt.plusMinutes(EnrollmentPolicy.NOTIFIED_ACCEPT_MINUTES))) {
            throw new IllegalStateException("수락 가능 시간이 초과되었습니다.");
        }
    }

    private boolean isNotifiedExpired(LocalDateTime now) {
        return status == WaitlistStatus.NOTIFIED
                && now.isAfter(notifiedAt.plusMinutes(EnrollmentPolicy.NOTIFIED_ACCEPT_MINUTES));
    }

}
