package com.liveklass.integration;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.global.exception.waitlist.WaitlistException;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class WaitlistServiceTest {

    @Autowired WaitlistService waitlistService;
    @Autowired UserRepository userRepository;
    @Autowired KlassRepository klassRepository;
    @Autowired WaitlistRepository waitlistRepository;

    User creator, student;
    Klass openKlass;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
        student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));

        Klass draft = Klass.create(creator, "스프링 강의", "설명", BigDecimal.valueOf(10000),
                1, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        draft.open(LocalDateTime.now());
        openKlass = klassRepository.save(draft);
    }

    @Nested
    @DisplayName("대기 등록")
    class JoinWaitlist {

        @Test
        @DisplayName("대기열에 등록하면 WAITING 상태로 첫 번째 순서에 저장된다")
        void shouldCreateWaitlistEntryAsFirstWaiting() {
            Waitlist waitlist = waitlistService.join(student.getId(), openKlass.getId());

            assertThat(waitlist.getId()).isNotNull();
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(waitlist.getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("두 번째 등록자는 position 2로 저장된다")
        void shouldAssignIncrementalPositionToSubsequentEntries() {
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));

            waitlistService.join(student.getId(), openKlass.getId());
            Waitlist second = waitlistService.join(student2.getId(), openKlass.getId());

            assertThat(second.getPosition()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("중복 대기 방지")
    class DuplicateWaitlist {

        @Test
        @DisplayName("WAITING 상태로 이미 대기 중인 강의에 다시 등록하면 거부된다")
        void shouldRejectDuplicateWaitlistWhenWaiting() {
            waitlistService.join(student.getId(), openKlass.getId());

            assertThatThrownBy(() -> waitlistService.join(student.getId(), openKlass.getId()))
                    .isInstanceOf(WaitlistException.class)
                    .hasMessageContaining("이미 대기");
        }

        @Test
        @DisplayName("NOTIFIED 상태에서 다시 대기 등록하면 거부된다")
        void shouldRejectDuplicateWaitlistWhenNotified() {
            waitlistService.join(student.getId(), openKlass.getId());
            waitlistService.notifyNext(openKlass.getId(), LocalDateTime.now());

            assertThatThrownBy(() -> waitlistService.join(student.getId(), openKlass.getId()))
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
            waitlistService.join(student.getId(), openKlass.getId());

            waitlistService.notifyNext(openKlass.getId(), LocalDateTime.now());

            Waitlist notified = waitlistRepository
                    .findFirstByKlassIdAndStatusOrderByPositionAsc(openKlass.getId(), WaitlistStatus.NOTIFIED)
                    .orElseThrow();
            assertThat(notified.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        }

        @Test
        @DisplayName("여러 대기자 중 position이 가장 낮은 대기자가 알림을 받는다")
        void shouldNotifyLowestPositionFirst() {
            User student2 = userRepository.save(User.create("수강생2", "student2@test.com", UserRole.STUDENT));
            Waitlist first = waitlistService.join(student.getId(), openKlass.getId());
            waitlistService.join(student2.getId(), openKlass.getId());

            waitlistService.notifyNext(openKlass.getId(), LocalDateTime.now());

            Waitlist notified = waitlistRepository
                    .findFirstByKlassIdAndStatusOrderByPositionAsc(openKlass.getId(), WaitlistStatus.NOTIFIED)
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
            LocalDateTime now = LocalDateTime.now();
            Waitlist waitlist = waitlistService.join(student.getId(), openKlass.getId());
            waitlistService.notifyNext(openKlass.getId(), now);

            waitlistService.convertToEnrollment(waitlist.getId(), now.plusMinutes(10));

            assertThat(waitlistRepository.findById(waitlist.getId()).orElseThrow().getStatus())
                    .isEqualTo(WaitlistStatus.CONVERTED);
        }
    }
}
