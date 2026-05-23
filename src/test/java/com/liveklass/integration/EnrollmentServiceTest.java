package com.liveklass.integration;

import com.liveklass.domain.enrollment.ChangedBy;
import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.enrollment.HistoryReason;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.global.exception.enrollment.EnrollmentException;
import com.liveklass.repository.EnrollmentHistoryRepository;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import com.liveklass.service.EnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EnrollmentServiceTest {

    @Autowired EnrollmentService enrollmentService;
    @Autowired UserRepository userRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired EnrollmentHistoryRepository historyRepository;

    User creator, student;
    Klass klass;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
        student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));
        klass = openKlass(BigDecimal.valueOf(10000), 30, 7);
    }

    private Klass openKlass(BigDecimal price, int maxCapacity, int cancellationDeadlineDays) {
        Klass draft = Klass.create(creator, "스프링 강의", "설명", price, maxCapacity,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), cancellationDeadlineDays);
        draft.open(LocalDateTime.now());
        return klassRepository.save(draft);
    }

    @Nested
    @DisplayName("수강 신청")
    class Enroll {

        @Test
        @DisplayName("수강 신청하면 PENDING 상태의 Enrollment가 생성된다")
        void shouldCreatePendingEnrollmentWhenEnrolled() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), LocalDateTime.now());

            assertThat(enrollment.getId()).isNotNull();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("무료 강의 신청 시 자동으로 CONFIRMED 상태가 된다")
        void shouldAutoConfirmFreeKlassEnrollment() {
            Klass freeKlass = openKlass(BigDecimal.ZERO, 30, 7);

            Enrollment enrollment = enrollmentService.enroll(student.getId(), freeKlass.getId(), LocalDateTime.now());

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("정원이 초과된 강의에 신청하면 거부된다")
        void shouldRejectEnrollmentWhenKlassIsFull() {
            Klass fullKlass = openKlass(BigDecimal.valueOf(10000), 1, 7);
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));
            LocalDateTime now = LocalDateTime.now();

            enrollmentService.enroll(student.getId(), fullKlass.getId(), now);

            assertThatThrownBy(() -> enrollmentService.enroll(student2.getId(), fullKlass.getId(), now))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("정원");
        }
    }

    @Nested
    @DisplayName("결제 확정")
    class ConfirmPayment {

        @Test
        @DisplayName("결제 확정하면 CONFIRMED 상태로 변경된다")
        void shouldConfirmEnrollmentAfterPayment() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);

            enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(10));

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("만료된 PENDING 상태에서 결제를 시도하면 EnrollmentException이 발생한다")
        void shouldThrowEnrollmentExceptionWhenPendingExpired() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);

            assertThatThrownBy(() -> enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(21)))
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
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);

            enrollmentService.cancel(enrollment.getId(), student.getId(), now.plusMinutes(5));

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("취소 가능 기간 내에 CONFIRMED 수강을 취소하면 CANCELLED 상태로 변경된다")
        void shouldCancelConfirmedEnrollmentWithinDeadline() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(10));

            enrollmentService.cancel(enrollment.getId(), student.getId(), now.plusDays(3));

            assertThat(enrollmentRepository.findById(enrollment.getId()).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("취소 가능 기간을 초과한 CONFIRMED 수강 취소 시도는 EnrollmentException이 발생한다")
        void shouldThrowEnrollmentExceptionWhenCancellationDeadlinePassed() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(10));

            assertThatThrownBy(() -> enrollmentService.cancel(enrollment.getId(), student.getId(), now.plusDays(8)))
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
            enrollmentService.enroll(student.getId(), klass.getId(), LocalDateTime.now());

            assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), klass.getId(), LocalDateTime.now()))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("이미 신청");
        }

        @Test
        @DisplayName("CONFIRMED 상태인 강의에 중복 신청하면 거부된다")
        void shouldRejectDuplicateEnrollmentWhenConfirmed() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(10));

            assertThatThrownBy(() -> enrollmentService.enroll(student.getId(), klass.getId(), now.plusMinutes(15)))
                    .isInstanceOf(EnrollmentException.class)
                    .hasMessageContaining("이미 신청");
        }

        @Test
        @DisplayName("CANCELLED 후 동일 강의에 재신청하면 PENDING으로 생성된다")
        void shouldAllowReEnrollmentAfterCancelled() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment first = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.cancel(first.getId(), student.getId(), now.plusMinutes(5));

            Enrollment reEnroll = enrollmentService.enroll(student.getId(), klass.getId(), now.plusMinutes(10));

            assertThat(reEnroll.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("이력 저장")
    class HistoryRecording {

        @Test
        @DisplayName("수강 신청 시 USER_ENROLL 이력이 저장된다")
        void shouldSaveUserEnrollHistoryOnEnroll() {
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), LocalDateTime.now());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).getReason()).isEqualTo(HistoryReason.USER_ENROLL);
            assertThat(histories.get(0).getChangedBy()).isEqualTo(ChangedBy.USER);
            assertThat(histories.get(0).getUserId()).isEqualTo(student.getId());
        }

        @Test
        @DisplayName("결제 확정 시 PAYMENT_CONFIRMED 이력이 저장된다")
        void shouldSavePaymentConfirmedHistoryOnConfirm() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.confirm(enrollment.getId(), student.getId(), now.plusMinutes(10));

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            EnrollmentHistory confirm = histories.get(1);
            assertThat(confirm.getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
            assertThat(confirm.getChangedBy()).isEqualTo(ChangedBy.USER);
        }

        @Test
        @DisplayName("사용자 취소 시 USER_CANCEL 이력이 저장된다")
        void shouldSaveUserCancelHistoryOnCancel() {
            LocalDateTime now = LocalDateTime.now();
            Enrollment enrollment = enrollmentService.enroll(student.getId(), klass.getId(), now);
            enrollmentService.cancel(enrollment.getId(), student.getId(), now.plusMinutes(5));

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            EnrollmentHistory cancel = histories.get(1);
            assertThat(cancel.getReason()).isEqualTo(HistoryReason.USER_CANCEL);
            assertThat(cancel.getChangedBy()).isEqualTo(ChangedBy.USER);
            assertThat(cancel.getUserId()).isEqualTo(student.getId());
        }

        @Test
        @DisplayName("무료 강의 자동 확정 시 SYSTEM이 처리자인 PAYMENT_CONFIRMED 이력이 저장된다")
        void shouldSaveSystemHistoryOnFreeKlassAutoConfirm() {
            Klass freeKlass = openKlass(BigDecimal.ZERO, 30, 7);
            Enrollment enrollment = enrollmentService.enroll(student.getId(), freeKlass.getId(), LocalDateTime.now());

            List<EnrollmentHistory> histories = historyRepository.findByEnrollmentId(enrollment.getId());

            assertThat(histories).hasSize(2);
            EnrollmentHistory autoConfirm = histories.get(1);
            assertThat(autoConfirm.getReason()).isEqualTo(HistoryReason.PAYMENT_CONFIRMED);
            assertThat(autoConfirm.getChangedBy()).isEqualTo(ChangedBy.SYSTEM);
            assertThat(autoConfirm.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("내 수강 신청 목록 조회")
    class ListMyEnrollments {

        @Test
        @DisplayName("내 수강 신청 목록을 조회할 수 있다")
        void shouldReturnMyEnrollmentList() {
            enrollmentService.enroll(student.getId(), klass.getId(), LocalDateTime.now());

            List<Enrollment> enrollments = enrollmentService.findByUser(student.getId());

            assertThat(enrollments).hasSize(1);
        }

        @Test
        @DisplayName("다른 사용자의 신청 건은 조회되지 않는다")
        void shouldNotReturnOtherUsersEnrollments() {
            User other = userRepository.save(User.create("다른수강생", "other@test.com", UserRole.STUDENT));
            enrollmentService.enroll(other.getId(), klass.getId(), LocalDateTime.now());

            List<Enrollment> enrollments = enrollmentService.findByUser(student.getId());

            assertThat(enrollments).isEmpty();
        }
    }
}
