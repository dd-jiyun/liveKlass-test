package com.liveklass.domain.enrollment;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    private static final User CREATOR = User.create("크리에이터", "creator@liveKlass.com", UserRole.CREATOR);
    private static final User STUDENT = User.create("수강생", "student@liveKlass.com", UserRole.STUDENT);

    private Klass openKlass(BigDecimal price, int maxCapacity, int cancellationDeadlineDays) {
        Klass klass = Klass.create(
                CREATOR, "테스트 강의", "테스트 설명", price, maxCapacity,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5),
                cancellationDeadlineDays
        );
        klass.open(LocalDateTime.now());
        return klass;
    }

    private Klass openKlass() {
        return openKlass(BigDecimal.valueOf(10000), 30, 7);
    }

    private Klass freeKlass() {
        return openKlass(BigDecimal.ZERO, 30, 7);
    }

    @Nested
    @DisplayName("수강 신청 기본")
    class EnrollBasic {

        @Test
        @DisplayName("수강 신청 시 PENDING 상태로 생성되고 수강 인원이 1 증가한다")
        void shouldCreatePendingEnrollmentAndIncreaseCount() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();

            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(klass.getEnrolledCount()).isEqualTo(1);
            assertThat(enrollment.getPendingExpiresAt()).isEqualTo(now.plusMinutes(20));
        }

        @Test
        @DisplayName("CLOSED 강의에 수강 신청하면 거부된다")
        void shouldFailEnrollmentWhenKlassClosed() {
            Klass klass = openKlass();
            klass.close();

            assertThatThrownBy(() -> Enrollment.create(STUDENT, klass, LocalDateTime.now()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("결제 확정")
    class ConfirmPayment {

        @Test
        @DisplayName("결제 완료 시 PENDING 상태가 CONFIRMED로 변경된다")
        void shouldChangeStatusToConfirmedAfterPayment() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.confirm(now.plusMinutes(10));

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("만료된 PENDING 상태에서 결제를 시도하면 거부된다")
        void shouldFailPaymentWhenPendingExpired() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            assertThatThrownBy(() -> enrollment.confirm(now.plusMinutes(21)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("만료");
        }

        @Test
        @DisplayName("이미 CONFIRMED 상태에서 결제를 다시 시도하면 거부된다")
        void shouldFailPaymentWhenAlreadyConfirmed() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.confirm(now.plusMinutes(10));

            assertThatThrownBy(() -> enrollment.confirm(now.plusMinutes(15)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("PENDING 만료")
    class PendingExpiry {

        @Test
        @DisplayName("PENDING이 20분 초과 시 CANCELLED로 변경되고 수강 인원이 1 감소한다")
        void shouldCancelPendingAndDecreaseCountWhenExpired() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.expireIfOverdue(now.plusMinutes(21));

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(klass.getEnrolledCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("PENDING이 20분 미만이면 만료되지 않고 PENDING 상태를 유지한다")
        void shouldStayPendingWhenNotOverdue() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.expireIfOverdue(now.plusMinutes(19));

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(klass.getEnrolledCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("수강 취소")
    class CancelEnrollment {

        @Test
        @DisplayName("취소 가능 기간 내에 CONFIRMED 수강을 취소하면 CANCELLED로 변경되고 수강 인원이 1 감소한다")
        void shouldCancelConfirmedEnrollmentWithinDeadline() {
            Klass klass = openKlass(BigDecimal.valueOf(10000), 30, 7);
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.confirm(now.plusMinutes(10));

            enrollment.cancel(now.plusDays(3));

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(klass.getEnrolledCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("취소 가능 기간을 초과한 CONFIRMED 수강 취소 시도는 거부된다")
        void shouldFailCancellationAfterDeadline() {
            Klass klass = openKlass(BigDecimal.valueOf(10000), 30, 7);
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.confirm(now.plusMinutes(10));

            assertThatThrownBy(() -> enrollment.cancel(now.plusDays(8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("취소 가능 기간");
        }

        @Test
        @DisplayName("PENDING 상태에서 수동 취소하면 즉시 CANCELLED로 변경되고 수강 인원이 1 감소한다")
        void shouldImmediatelyCancelPendingEnrollment() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.cancel(now.plusMinutes(5));

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(klass.getEnrolledCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("취소 불가 강의의 CONFIRMED 수강을 취소하면 거부된다")
        void shouldFailCancellationWhenKlassIsNotCancellable() {
            Klass klass = openKlass(BigDecimal.valueOf(10000), 30, 0);
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.confirm(now.plusMinutes(10));

            assertThatThrownBy(() -> enrollment.cancel(now.plusDays(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("취소가 불가능");
        }

        @Test
        @DisplayName("이미 CANCELLED 상태에서 취소를 시도하면 거부된다")
        void shouldFailCancellationWhenAlreadyCancelled() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.cancel(now.plusMinutes(5));

            assertThatThrownBy(() -> enrollment.cancel(now.plusMinutes(10)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("무료 강의 자동 확정")
    class FreeKlassAutoConfirm {

        @Test
        @DisplayName("무료 강의 신청 시 자동으로 CONFIRMED 상태가 된다")
        void shouldAutoConfirmFreeKlassEnrollment() {
            Klass klass = freeKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.autoConfirmIfFree(now);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("유료 강의에서 autoConfirmIfFree를 호출해도 PENDING 상태를 유지한다")
        void shouldStayPendingWhenKlassIsPaid() {
            Klass klass = openKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);

            enrollment.autoConfirmIfFree(now);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("무료 강의 자동 확정 이력에는 처리자가 SYSTEM이고 사유가 PAYMENT_CONFIRMED이다")
        void shouldRecordSystemAsChangerForFreeKlassAutoConfirm() {
            Klass klass = freeKlass();
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = Enrollment.create(STUDENT, klass, now);
            enrollment.autoConfirmIfFree(now);

            EnrollmentHistory history = EnrollmentHistory.record(
                    enrollment,
                    EnrollmentStatus.PENDING,
                    EnrollmentStatus.CONFIRMED,
                    HistoryReason.PAYMENT_CONFIRMED,
                    ChangedBy.SYSTEM,
                    null
            );

            assertThat(history.getChangedBy()).isEqualTo(ChangedBy.SYSTEM);
            assertThat(history.getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
            assertThat(history.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("OPEN 이후 핵심 필드 변경 불가")
    class FieldChangeAfterOpen {

        @Test
        @DisplayName("OPEN 이후 수강료 변경 시도는 거부된다")
        void shouldFailPriceChangeAfterOpen() {
            Klass klass = openKlass();

            assertThatThrownBy(() -> klass.changePrice(BigDecimal.valueOf(5000)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("수강료");
        }

        @Test
        @DisplayName("OPEN 이후 최대 정원 변경 시도는 거부된다")
        void shouldFailMaxCapacityChangeAfterOpen() {
            Klass klass = openKlass();

            assertThatThrownBy(() -> klass.changeMaxCapacity(10))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("정원");
        }
    }
}
