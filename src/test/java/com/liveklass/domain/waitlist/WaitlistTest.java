package com.liveklass.domain.waitlist;

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

class WaitlistTest {

    private static final User CREATOR = User.create("크리에이터", "creator@liveKlass.com", UserRole.CREATOR);
    private static final User STUDENT = User.create("수강생", "student@liveKlass.com", UserRole.STUDENT);

    private Klass openKlass() {
        Klass klass = Klass.create(
                CREATOR, "테스트 강의",
                "테스트 설명",
                BigDecimal.valueOf(10000),
                1,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5),
                7
        );
        klass.open(LocalDateTime.now());
        return klass;
    }

    @Nested
    @DisplayName("대기 등록")
    class WaitlistJoin {

        @Test
        @DisplayName("정원 초과 OPEN 강의에 대기 등록하면 WAITING 상태로 생성된다")
        void shouldCreateWaitingStatusWhenJoiningOpenKlass() {
            Klass klass = openKlass();

            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(waitlist.getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("CLOSED 강의에 대기 등록을 시도하면 거부된다")
        void shouldFailJoiningWhenKlassClosed() {
            Klass klass = openKlass();
            klass.close();

            assertThatThrownBy(() -> Waitlist.create(STUDENT, klass, 1))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("NOTIFIED 상태 처리")
    class NotifiedHandling {

        @Test
        @DisplayName("WAITING 상태에서 알림을 받으면 NOTIFIED로 전환된다")
        void shouldChangeStatusToNotifiedWhenNotified() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);
            LocalDateTime now = LocalDateTime.now();

            waitlist.markAsNotified(now);

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
            assertThat(waitlist.getNotifiedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("NOTIFIED 상태에서 수락 시간 내에 신청하면 CONVERTED로 전환된다")
        void shouldConvertToConvertedWithinAcceptWindow() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);
            LocalDateTime now = LocalDateTime.now();
            waitlist.markAsNotified(now);

            waitlist.convert(now.plusMinutes(10));

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CONVERTED);
        }

        @Test
        @DisplayName("NOTIFIED 상태에서 20분이 초과되면 EXPIRED로 전환된다")
        void shouldExpireWhenAcceptWindowPassed() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);
            LocalDateTime now = LocalDateTime.now();
            waitlist.markAsNotified(now);

            waitlist.expireIfOverdue(now.plusMinutes(21));

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.EXPIRED);
        }

        @Test
        @DisplayName("NOTIFIED 상태에서 20분 미만이면 만료되지 않는다")
        void shouldStayNotifiedWithinAcceptWindow() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);
            LocalDateTime now = LocalDateTime.now();
            waitlist.markAsNotified(now);

            waitlist.expireIfOverdue(now.plusMinutes(19));

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.NOTIFIED);
        }
    }

    @Nested
    @DisplayName("수동 CLOSED 시 대기자 처리")
    class ManualCloseHandling {

        @Test
        @DisplayName("WAITING 상태 대기자는 수동 취소 시 CANCELLED로 전환된다")
        void shouldCancelWaitingOnManualClose() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);

            waitlist.cancelByCreator();

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        }

        @Test
        @DisplayName("NOTIFIED 상태 대기자는 수동 취소 시 CANCELLED로 전환된다")
        void shouldCancelNotifiedOnManualClose() {
            Klass klass = openKlass();
            Waitlist waitlist = Waitlist.create(STUDENT, klass, 1);
            waitlist.markAsNotified(LocalDateTime.now());

            waitlist.cancelByCreator();

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        }
    }
}
