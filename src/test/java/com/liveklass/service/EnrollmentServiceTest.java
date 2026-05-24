package com.liveklass.service;

import com.liveklass.domain.enrollment.ChangedBy;
import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.enrollment.HistoryReason;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.global.exception.enrollment.EnrollmentException;
import com.liveklass.repository.EnrollmentHistoryRepository;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EnrollmentServiceTest {

    @Autowired
    EnrollmentService enrollmentService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    KlassRepository klassRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    EnrollmentHistoryRepository historyRepository;
    @MockitoBean
    Clock clock;

    User creator, student;
    Klass klass;
    Instant base;

    @BeforeEach
    void setUp() {
        base = Instant.now();
        fixClock(base);

        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
        student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));
        klass = openKlass(BigDecimal.valueOf(10000), 30, 7);
    }

    private Klass openKlass(BigDecimal price, int maxCapacity, int cancellationDeadlineDays) {
        Klass draft = Klass.create(creator, "스프링 강의", "설명", price, maxCapacity,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), cancellationDeadlineDays);
        draft.open(LocalDateTime.now(clock));
        return klassRepository.save(draft);
    }

    private void fixClock(Instant instant) {
        given(clock.instant()).willReturn(instant);
        given(clock.getZone()).willReturn(ZoneId.systemDefault());
    }

    private void advanceClock(long minutes) {
        fixClock(base.plus(minutes, ChronoUnit.MINUTES));
    }

    @Nested
    @DisplayName("수강 신청")
    class Enroll {

        @Test
        @DisplayName("수강 신청하면 PENDING 상태의 Enrollment가 생성된다")
        void shouldCreatePendingEnrollmentWhenEnrolled() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());

            assertThat(enrollment.getId()).isNotNull();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("무료 강의 신청 시 자동으로 CONFIRMED 상태가 된다")
        void shouldAutoConfirmFreeKlassEnrollment() {
            Klass freeKlass = openKlass(BigDecimal.ZERO, 30, 7);

            Enrollment enrollment = enrollmentService.enroll(student.getId(), freeKlass.getId());

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("정원이 초과된 강의에 신청하면 거부된다")
        void shouldRejectEnrollmentWhenKlassIsFull() {
            Klass fullKlass = openKlass(BigDecimal.valueOf(10000), 1, 7);
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));

            enrollmentService.enroll(student.getId(), fullKlass.getId());

            assertThatThrownBy(() -> enrollmentService.enroll(student2.getId(), fullKlass.getId()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("정원");
        }

        @Test
        @DisplayName("CREATOR는 다른 크리에이터의 OPEN 강의에 수강 신청할 수 있다")
        void shouldAllowCreatorToEnrollInOtherCreatorsKlass() {
            User otherCreator = userRepository.save(User.create("다른크리에이터", "other@test.com", UserRole.CREATOR));

            Enrollment enrollment = enrollmentService.enroll(otherCreator.getId(), klass.getId());

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(enrollment.getKlass().getId()).isEqualTo(klass.getId());
        }

        @Test
        @DisplayName("STUDENT에서 CREATOR로 승격된 사용자도 수강 신청할 수 있다")
        void shouldAllowPromotedCreatorToEnroll() {
            User promoted = userRepository.save(User.create("승격유저", "promoted@test.com", UserRole.STUDENT));
            promoted.promoteToCreator();

            Enrollment enrollment = enrollmentService.enroll(promoted.getId(), klass.getId());

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("결제 확정")
    class ConfirmPayment {

        @Test
        @DisplayName("결제 확정하면 CONFIRMED 상태로 변경된다")
        void shouldConfirmEnrollmentAfterPayment() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());

            enrollmentService.confirm(enrollment.getId(), student.getId());

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("PENDING 만료 후 결제를 시도하면 EnrollmentException이 발생한다")
        void shouldThrowEnrollmentExceptionWhenPendingExpired() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());

            advanceClock(EnrollmentPolicy.PENDING_EXPIRE_MINUTES + 1);

            assertThatThrownBy(() -> enrollmentService.confirm(enrollment.getId(), student.getId()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("만료");
        }
    }

    @Nested
    @DisplayName("수강 취소")
    class CancelEnrollment {

        @Test
        @DisplayName("PENDING 상태를 취소하면 CANCELLED 상태로 변경된다")
        void shouldCancelPendingEnrollmentSuccessfully() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());

            enrollmentService.cancel(enrollment.getId(), student.getId());

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("취소 가능 기간 내에 CONFIRMED 수강을 취소하면 CANCELLED 상태로 변경된다")
        void shouldCancelConfirmedEnrollmentWithinDeadline() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());
            enrollmentService.confirm(enrollment.getId(), student.getId());

            enrollmentService.cancel(enrollment.getId(), student.getId());

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("취소 가능 기간을 초과한 CONFIRMED 수강 취소 시도는 EnrollmentException이 발생한다")
        void shouldThrowEnrollmentExceptionWhenCancellationDeadlinePassed() {
            // cancellationDeadlineDays(11) > daysUntilStart(10) → 취소 기한이 이미 지남
            Klass pastDeadlineKlass = openKlass(BigDecimal.valueOf(10000), 30, 11);
            Enrollment enrollment = enrollmentService.enroll(student.getId(), pastDeadlineKlass.getId());
            enrollmentService.confirm(enrollment.getId(), student.getId());

            assertThatThrownBy(() -> enrollmentService.cancel(enrollment.getId(), student.getId()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("취소 가능 기간");
        }
    }

    @Nested
    @DisplayName("중복 신청 방지")
    class DuplicateEnrollment {

        @Test
        @DisplayName("PENDING 상태인 강의에 중복 신청하면 거부된다")
        void shouldRejectDuplicateEnrollmentWhenPending() {
            enrollmentService.enroll(student.getId(), klass.getId());

            assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), klass.getId()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("이미 신청");
        }

        @Test
        @DisplayName("CONFIRMED 상태인 강의에 중복 신청하면 거부된다")
        void shouldRejectDuplicateEnrollmentWhenConfirmed() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());
            enrollmentService.confirm(enrollment.getId(), student.getId());

            assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), klass.getId()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("이미 신청");
        }

        @Test
        @DisplayName("CANCELLED 후 동일 강의에 재신청하면 PENDING으로 생성된다")
        void shouldAllowReEnrollmentAfterCancelled() {
            Enrollment first = enrollmentService.enroll(student.getId(), klass.getId());
            enrollmentService.cancel(first.getId(), student.getId());

            Enrollment reEnroll = enrollmentService.enroll(student.getId(), klass.getId());

            assertThat(reEnroll.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("이력 저장")
    class HistoryRecording {

        @Test
        @DisplayName("수강 신청 시 USER_ENROLL 이력이 저장된다")
        void shouldSaveUserEnrollHistoryOnEnroll() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).getReason()).isEqualTo(HistoryReason.USER_ENROLL);
            assertThat(histories.get(0).getChangedBy()).isEqualTo(ChangedBy.USER);
            assertThat(histories.get(0).getUserId()).isEqualTo(student.getId());
        }

        @Test
        @DisplayName("결제 확정 시 PAYMENT_CONFIRMED 이력이 저장된다")
        void shouldSavePaymentConfirmedHistoryOnConfirm() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());
            enrollmentService.confirm(enrollment.getId(), student.getId());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            assertThat(histories.get(1).getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
            assertThat(histories.get(1).getChangedBy()).isEqualTo(ChangedBy.USER);
        }

        @Test
        @DisplayName("사용자 취소 시 USER_CANCEL 이력이 저장된다")
        void shouldSaveUserCancelHistoryOnCancel() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId());
            enrollmentService.cancel(enrollment.getId(), student.getId());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            assertThat(histories.get(1).getReason()).isEqualTo(HistoryReason.USER_CANCEL);
            assertThat(histories.get(1).getChangedBy()).isEqualTo(ChangedBy.USER);
            assertThat(histories.get(1).getUserId()).isEqualTo(student.getId());
        }

        @Test
        @DisplayName("무료 강의 자동 확정 시 SYSTEM이 처리자인 PAYMENT_CONFIRMED 이력이 저장된다")
        void shouldSaveSystemHistoryOnFreeKlassAutoConfirm() {
            Klass freeKlass = openKlass(BigDecimal.ZERO, 30, 7);
            Enrollment enrollment = enrollmentService.enroll(student.getId(), freeKlass.getId());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            assertThat(histories.get(1).getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
            assertThat(histories.get(1).getChangedBy()).isEqualTo(ChangedBy.SYSTEM);
            assertThat(histories.get(1).getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("내 수강 신청 목록 조회")
    class ListMyEnrollments {

        @Test
        @DisplayName("내 수강 신청 목록을 조회할 수 있다")
        void shouldReturnMyEnrollmentList() {
            enrollmentService.enroll(student.getId(), klass.getId());

            List<Enrollment> enrollments = enrollmentService.findByUser(student.getId());

            assertThat(enrollments).hasSize(1);
        }

        @Test
        @DisplayName("다른 사용자의 신청 건은 조회되지 않는다")
        void shouldNotReturnOtherUsersEnrollments() {
            User other = userRepository.save(User.create("다른수강생", "other@test.com", UserRole.STUDENT));
            enrollmentService.enroll(other.getId(), klass.getId());

            List<Enrollment> enrollments = enrollmentService.findByUser(student.getId());

            assertThat(enrollments).isEmpty();
        }
    }
}
