package com.liveklass.domain.enrollment;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentHistoryTest {

    private static final User CREATOR = User.create("크리에이터", "creator@liveKlass.com", UserRole.CREATOR);
    private static final User STUDENT = User.create("수강생", "student@liveKlass.com", UserRole.STUDENT);
    private static final LocalDateTime NOW = LocalDateTime.now();

    private Klass openKlass(BigDecimal price) {
        Klass klass = Klass.create(
                CREATOR, "테스트 강의", "테스트 설명", price, 30,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5),
                7
        );
        klass.open(NOW);
        return klass;
    }

    @Test
    @DisplayName("수강 신청 이력에는 사유가 USER_ENROLL이고 처리자가 USER이다")
    void shouldRecordUserEnrollReasonOnEnrollment() {
        Klass klass = openKlass(BigDecimal.valueOf(10000));
        Enrollment enrollment = Enrollment.create(STUDENT, klass, NOW);

        EnrollmentHistory history = EnrollmentHistory.record(
                enrollment, null, EnrollmentStatus.PENDING,
                HistoryReason.USER_ENROLL, ChangedBy.USER, 1L
        );

        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(history.getReason()).isEqualTo(HistoryReason.USER_ENROLL);
        assertThat(history.getChangedBy()).isEqualTo(ChangedBy.USER);
        assertThat(history.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("결제 완료 이력에는 사유가 PAYMENT_CONFIRMED이고 처리자가 USER이다")
    void shouldRecordPaymentConfirmedReasonOnPayment() {
        Klass klass = openKlass(BigDecimal.valueOf(10000));
        Enrollment enrollment = Enrollment.create(STUDENT, klass, NOW);
        enrollment.confirm(NOW.plusMinutes(10));

        EnrollmentHistory history = EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.USER, 1L
        );

        assertThat(history.getFromStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(history.getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
        assertThat(history.getChangedBy()).isEqualTo(ChangedBy.USER);
    }

    @Test
    @DisplayName("사용자 취소 이력에는 사유가 USER_CANCEL이고 처리자가 USER이다")
    void shouldRecordUserCancelReasonOnCancellation() {
        Klass klass = openKlass(BigDecimal.valueOf(10000));
        Enrollment enrollment = Enrollment.create(STUDENT, klass, NOW);
        enrollment.cancel(NOW.plusMinutes(5));

        EnrollmentHistory history = EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CANCELLED,
                HistoryReason.USER_CANCEL, ChangedBy.USER, 1L
        );

        assertThat(history.getReason()).isEqualTo(HistoryReason.USER_CANCEL);
        assertThat(history.getChangedBy()).isEqualTo(ChangedBy.USER);
    }

    @Test
    @DisplayName("PENDING 만료 이력에는 사유가 PENDING_EXPIRED이고 처리자가 SYSTEM이며 userId가 null이다")
    void shouldRecordSystemAsChangerWhenPendingExpires() {
        Klass klass = openKlass(BigDecimal.valueOf(10000));
        Enrollment enrollment = Enrollment.create(STUDENT, klass, NOW);
        enrollment.expireIfOverdue(NOW.plusMinutes(21));

        EnrollmentHistory history = EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CANCELLED,
                HistoryReason.PENDING_EXPIRED, ChangedBy.SYSTEM, null
        );

        assertThat(history.getReason()).isEqualTo(HistoryReason.PENDING_EXPIRED);
        assertThat(history.getChangedBy()).isEqualTo(ChangedBy.SYSTEM);
        assertThat(history.getUserId()).isNull();
    }

    @Test
    @DisplayName("무료 강의 자동 확정 이력에는 사유가 PAYMENT_CONFIRMED이고 처리자가 SYSTEM이며 userId가 null이다")
    void shouldRecordSystemAsChangerWhenFreeKlassAutoConfirms() {
        Klass klass = openKlass(BigDecimal.ZERO);
        Enrollment enrollment = Enrollment.create(STUDENT, klass, NOW);
        enrollment.autoConfirmIfFree(NOW);

        EnrollmentHistory history = EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.SYSTEM, null
        );

        assertThat(history.getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
        assertThat(history.getChangedBy()).isEqualTo(ChangedBy.SYSTEM);
        assertThat(history.getUserId()).isNull();
    }
}
