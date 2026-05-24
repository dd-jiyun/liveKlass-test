package com.liveklass.integration;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.global.exception.waitlist.WaitlistException;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import com.liveklass.repository.WaitlistRepository;
import com.liveklass.service.WaitlistService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(WaitlistServiceTest.TestClockConfig.class)
@Transactional
class WaitlistServiceTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(Instant.now(), ZoneId.systemDefault());
        }
    }

    @Autowired WaitlistService waitlistService;
    @Autowired UserRepository userRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired WaitlistRepository waitlistRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired Clock clock;

    User creator, student;
    Klass fullKlass;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
        student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));

        Klass draft = Klass.create(creator, "스프링 강의", "설명", BigDecimal.valueOf(10000),
                1, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        draft.open(LocalDateTime.now(clock));
        fullKlass = klassRepository.save(draft);

        User filler = userRepository.save(User.create("선점자", "filler@test.com", UserRole.STUDENT));
        Enrollment fillerEnrollment = Enrollment.create(filler, fullKlass, LocalDateTime.now(clock));
        enrollmentRepository.save(fillerEnrollment);
    }

    @Nested
    @DisplayName("대기 등록")
    class JoinWaitlist {

        @Test
        @DisplayName("대기열에 등록하면 WAITING 상태로 첫 번째 순서에 저장된다")
        void shouldCreateWaitlistEntryAsFirstWaiting() {
            Waitlist waitlist = waitlistService.join(student.getId(), fullKlass.getId());

            assertThat(waitlist.getId()).isNotNull();
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(waitlist.getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("두 번째 등록자는 position 2로 저장된다")
        void shouldAssignIncrementalPositionToSubsequentEntries() {
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));

            waitlistService.join(student.getId(), fullKlass.getId());
            Waitlist second = waitlistService.join(student2.getId(), fullKlass.getId());

            assertThat(second.getPosition()).isEqualTo(2);
        }

        @Test
        @DisplayName("정원이 남아 있는 강의에 대기 등록하면 거부된다")
        void shouldRejectWaitlistWhenKlassIsNotFull() {
            Klass notFullKlass = Klass.create(creator, "여유 강의", "설명", BigDecimal.valueOf(10000),
                    10, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);
            notFullKlass.open(LocalDateTime.now(clock));
            klassRepository.save(notFullKlass);

            assertThatThrownBy(() -> waitlistService.join(student.getId(), notFullKlass.getId()))
                    .isInstanceOf(WaitlistException.class)
                    .hasMessageContaining("정원이 남아");
        }
    }

    @Nested
    @DisplayName("중복 대기 방지")
    class DuplicateWaitlist {

        @Test
        @DisplayName("WAITING 상태로 이미 대기 중인 강의에 다시 등록하면 거부된다")
        void shouldRejectDuplicateWaitlistWhenWaiting() {
            waitlistService.join(student.getId(), fullKlass.getId());

            assertThatThrownBy(() -> waitlistService.join(student.getId(), fullKlass.getId()))
                    .isInstanceOf(WaitlistException.class)
                    .hasMessageContaining("이미 대기");
        }

        @Test
        @DisplayName("NOTIFIED 상태에서 다시 대기 등록하면 거부된다")
        void shouldRejectDuplicateWaitlistWhenNotified() {
            waitlistService.join(student.getId(), fullKlass.getId());
            waitlistService.notifyNext(fullKlass.getId());

            assertThatThrownBy(() -> waitlistService.join(student.getId(), fullKlass.getId()))
                    .isInstanceOf(WaitlistException.class)
                    .hasMessageContaining("이미 대기");
        }
    }

    @Nested
    @DisplayName("대기 알림")
    class NotifyNext {

        @Test
        @DisplayName("대기열의 첫 번째 항목에 알림을 보내면 NOTIFIED 상태로 변경된다")
        void shouldNotifyFirstWaitlistEntry() {
            waitlistService.join(student.getId(), fullKlass.getId());

            waitlistService.notifyNext(fullKlass.getId());

            Waitlist notified = waitlistRepository
                    .findFirstByKlassIdAndStatusOrderByPositionAsc(fullKlass.getId(), WaitlistStatus.NOTIFIED)
                    .orElseThrow();
            assertThat(notified.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        }

        @Test
        @DisplayName("여러 대기자 중 position이 가장 낮은 대기자가 알림을 받는다")
        void shouldNotifyLowestPositionFirst() {
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));
            Waitlist first = waitlistService.join(student.getId(), fullKlass.getId());
            waitlistService.join(student2.getId(), fullKlass.getId());

            waitlistService.notifyNext(fullKlass.getId());

            Waitlist notified = waitlistRepository
                    .findFirstByKlassIdAndStatusOrderByPositionAsc(fullKlass.getId(), WaitlistStatus.NOTIFIED)
                    .orElseThrow();
            assertThat(notified.getId()).isEqualTo(first.getId());
        }
    }

    @Nested
    @DisplayName("수강 신청 전환")
    class ConvertToEnrollment {

        @Test
        @DisplayName("NOTIFIED 대기 항목을 전환하면 CONVERTED 상태가 된다")
        void shouldConvertNotifiedWaitlistToEnrollment() {
            Waitlist waitlist = waitlistService.join(student.getId(), fullKlass.getId());
            waitlistService.notifyNext(fullKlass.getId());

            waitlistService.convertToEnrollment(waitlist.getId(), student.getId());

            assertThat(waitlistRepository.findById(waitlist.getId()).orElseThrow().getStatus())
                    .isEqualTo(WaitlistStatus.CONVERTED);
        }

        @Test
        @DisplayName("다른 사용자의 대기 항목을 전환하려 하면 거부된다")
        void shouldRejectConversionByNonOwner() {
            Waitlist waitlist = waitlistService.join(student.getId(), fullKlass.getId());
            waitlistService.notifyNext(fullKlass.getId());

            User other = userRepository.save(User.create("타인", "other@test.com", UserRole.STUDENT));

            assertThatThrownBy(() -> waitlistService.convertToEnrollment(waitlist.getId(), other.getId()))
                    .isInstanceOf(WaitlistException.class)
                    .hasMessageContaining("본인의 대기 항목");
        }
    }
}
