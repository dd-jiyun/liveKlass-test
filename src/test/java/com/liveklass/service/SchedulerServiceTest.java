package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import com.liveklass.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(SchedulerServiceTest.TestClockConfig.class)
@Transactional
class SchedulerServiceTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(Instant.now(), ZoneId.systemDefault());
        }
    }

    @Autowired SchedulerService schedulerService;
    @Autowired UserRepository userRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired WaitlistRepository waitlistRepository;
    @Autowired Clock clock;

    User creator, student, student2;
    Klass klass;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
        student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));
        student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));

        Klass draft = Klass.create(creator, "스프링 강의", "설명", BigDecimal.valueOf(10000),
                2, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        draft.open(LocalDateTime.now(clock));
        klass = klassRepository.save(draft);
    }

    @Nested
    @DisplayName("PENDING 수강신청 자동 만료")
    class ExpirePendingEnrollments {

        @Test
        @DisplayName("만료 시간이 지난 PENDING 수강신청은 CANCELLED로 전환된다")
        void shouldCancelOverduePendingEnrollment() {
            Enrollment enrollment = Enrollment.create(student, klass, LocalDateTime.now(clock));
            // pendingExpiresAt을 과거로 강제 설정하기 위해 과거 시간으로 생성
            Enrollment pastEnrollment = Enrollment.create(
                    student, klass,
                    LocalDateTime.now(clock).minus(EnrollmentPolicy.PENDING_EXPIRE_MINUTES + 1, ChronoUnit.MINUTES));
            enrollmentRepository.save(pastEnrollment);

            schedulerService.expirePendingEnrollments();

            Enrollment found = enrollmentRepository.findById(pastEnrollment.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("만료 시간이 지나지 않은 PENDING 수강신청은 CANCELLED 처리되지 않는다")
        void shouldNotCancelActivePendingEnrollment() {
            Enrollment enrollment = Enrollment.create(student, klass, LocalDateTime.now(clock));
            enrollmentRepository.save(enrollment);

            schedulerService.expirePendingEnrollments();

            Enrollment found = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("PENDING 만료 시 enrolledCount가 감소한다")
        void shouldDecreaseEnrolledCountOnPendingExpiry() {
            Enrollment pastEnrollment = Enrollment.create(
                    student, klass,
                    LocalDateTime.now(clock).minus(EnrollmentPolicy.PENDING_EXPIRE_MINUTES + 1, ChronoUnit.MINUTES));
            enrollmentRepository.save(pastEnrollment);
            int countBefore = klassRepository.findById(klass.getId()).orElseThrow().getEnrolledCount();

            schedulerService.expirePendingEnrollments();

            int countAfter = klassRepository.findById(klass.getId()).orElseThrow().getEnrolledCount();
            assertThat(countAfter).isLessThan(countBefore);
        }
    }

    @Nested
    @DisplayName("NOTIFIED 대기열 자동 만료")
    class ExpireNotifiedWaitlists {

        @Test
        @DisplayName("수락 시간이 지난 NOTIFIED 대기 항목은 EXPIRED로 전환된다")
        void shouldExpireOverdueNotifiedWaitlist() {
            // 만료된 시간에 NOTIFIED된 대기 항목 설정 (maxCapacity=1인 강의에서 진행)
            Klass fullKlass = Klass.create(creator, "꽉 찬 강의", "설명", BigDecimal.valueOf(10000),
                    1, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);
            fullKlass.open(LocalDateTime.now(clock));
            klassRepository.save(fullKlass);

            Enrollment filler = Enrollment.create(student2, fullKlass, LocalDateTime.now(clock));
            enrollmentRepository.save(filler);

            Waitlist waitlist = Waitlist.create(student, fullKlass, 1);
            // 수락 기간이 지난 시점에 NOTIFIED 상태로 설정
            waitlist.markAsNotified(
                    LocalDateTime.now(clock).minus(EnrollmentPolicy.NOTIFIED_ACCEPT_MINUTES + 1, ChronoUnit.MINUTES));
            waitlistRepository.save(waitlist);

            schedulerService.expireNotifiedWaitlists();

            Waitlist found = waitlistRepository.findById(waitlist.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(WaitlistStatus.EXPIRED);
        }

        @Test
        @DisplayName("수락 기간 내의 NOTIFIED 대기 항목은 EXPIRED 처리되지 않는다")
        void shouldNotExpireActiveNotifiedWaitlist() {
            Klass fullKlass = Klass.create(creator, "꽉 찬 강의", "설명", BigDecimal.valueOf(10000),
                    1, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);
            fullKlass.open(LocalDateTime.now(clock));
            klassRepository.save(fullKlass);

            Enrollment filler = Enrollment.create(student2, fullKlass, LocalDateTime.now(clock));
            enrollmentRepository.save(filler);

            Waitlist waitlist = Waitlist.create(student, fullKlass, 1);
            waitlist.markAsNotified(LocalDateTime.now(clock));
            waitlistRepository.save(waitlist);

            schedulerService.expireNotifiedWaitlists();

            Waitlist found = waitlistRepository.findById(waitlist.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        }
    }

    @Nested
    @DisplayName("OPEN 강의 자동 마감")
    class CloseExpiredKlasses {

        @Test
        @DisplayName("신청 마감일이 지난 OPEN 강의는 CLOSED로 전환된다")
        void shouldCloseKlassWhenEnrollmentDeadlinePassed() {
            Klass expiredKlass = Klass.create(creator, "마감된 강의", "설명", BigDecimal.valueOf(10000),
                    10, LocalDate.now().plusDays(5), LocalDate.now().plusDays(40),
                    LocalDate.now().minusDays(1), 7); // 마감일이 어제
            expiredKlass.open(LocalDateTime.now(clock).minusDays(10));
            klassRepository.save(expiredKlass);

            schedulerService.closeExpiredKlasses();

            Klass found = klassRepository.findById(expiredKlass.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(KlassStatus.CLOSED);
        }

        @Test
        @DisplayName("신청 마감일이 남은 OPEN 강의는 CLOSED 처리되지 않는다")
        void shouldNotCloseKlassBeforeEnrollmentDeadline() {
            schedulerService.closeExpiredKlasses();

            Klass found = klassRepository.findById(klass.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(KlassStatus.OPEN);
        }
    }
}
