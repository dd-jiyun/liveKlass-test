package com.liveklass.domain.klass;

import com.liveklass.domain.enrollment.EnrollmentPolicy;
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

class KlassTest {

    private static final User CREATOR = User.create("크리에이터", "creator@test.com", UserRole.CREATOR);

    private Klass draftKlass(LocalDate enrollmentDeadline) {
        return Klass.create(
                CREATOR,
                "테스트 강의",
                "강의 설명",
                BigDecimal.valueOf(10000),
                30,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40),
                enrollmentDeadline,
                7
        );
    }

    @Nested
    @DisplayName("DRAFT → OPEN 전환")
    class OpenTransition {

        @Test
        @DisplayName("수강 마감일이 미래이면 OPEN 전환에 성공한다")
        void shouldOpenWhenEnrollmentDeadlineIsFuture() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));

            klass.open(LocalDateTime.now());

            assertThat(klass.getStatus()).isEqualTo(KlassStatus.OPEN);
        }

        @Test
        @DisplayName("수강 마감일이 과거이면 OPEN 전환이 거부된다")
        void shouldFailToOpenWhenEnrollmentDeadlineIsPast() {
            Klass klass = draftKlass(LocalDate.now().minusDays(1));

            assertThatThrownBy(() -> klass.open(LocalDateTime.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("마감일");
        }

        @Test
        @DisplayName("DRAFT 상태 강의에 수강 신청하면 거부된다")
        void shouldFailEnrollmentWhenKlassIsDraft() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));

            assertThatThrownBy(() -> klass.validateEnrollable(LocalDateTime.now()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("신청");
        }
    }

    @Nested
    @DisplayName("OPEN → CLOSED 전환")
    class CloseTransition {

        @Test
        @DisplayName("마감일에 도달하면 강의가 자동으로 CLOSED된다")
        void shouldCloseAutomaticallyWhenDeadlineReached() {
            Klass klass = draftKlass(LocalDate.now().plusDays(1));
            klass.open(LocalDateTime.now());

            klass.closeIfDeadlineReached(LocalDateTime.now().plusDays(2));

            assertThat(klass.getStatus()).isEqualTo(KlassStatus.CLOSED);
        }

        @Test
        @DisplayName("크리에이터가 강의를 수동으로 CLOSED 전환할 수 있다")
        void shouldCloseManuallyByCreator() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));
            klass.open(LocalDateTime.now());

            klass.close();

            assertThat(klass.getStatus()).isEqualTo(KlassStatus.CLOSED);
        }

        @Test
        @DisplayName("정원이 모두 차도 강의는 OPEN 상태를 유지한다")
        void shouldStayOpenWhenCapacityFull() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));
            klass.open(LocalDateTime.now());

            for (int i = 0; i < 30; i++) klass.increaseEnrolledCount();

            assertThat(klass.getStatus()).isEqualTo(KlassStatus.OPEN);
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN 재오픈")
    class ReopenTransition {

        @Test
        @DisplayName("마감일 연장 후 재오픈에 성공한다")
        void shouldReopenAfterDeadlineExtended() {
            Klass klass = draftKlass(LocalDate.now().plusDays(1));
            klass.open(LocalDateTime.now());
            klass.closeIfDeadlineReached(LocalDateTime.now().plusDays(2));

            klass.extendDeadline(LocalDate.now().plusDays(9));
            klass.open(LocalDateTime.now());

            assertThat(klass.getStatus()).isEqualTo(KlassStatus.OPEN);
        }

        @Test
        @DisplayName("마감일 연장 없이 재오픈을 시도하면 거부된다")
        void shouldFailReopenWhenDeadlineNotExtended() {
            Klass klass = draftKlass(LocalDate.now().plusDays(1));
            klass.open(LocalDateTime.now());
            LocalDateTime afterDeadline = LocalDateTime.now().plusDays(2);
            klass.closeIfDeadlineReached(afterDeadline);

            assertThatThrownBy(() -> klass.open(afterDeadline))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("마감일");
        }
    }

    @Nested
    @DisplayName("수강 신청 가능 여부 검증")
    class EnrollableValidation {

        @Test
        @DisplayName("OPEN 상태이지만 마감일이 지난 강의에 수강 신청하면 거부된다")
        void shouldFailEnrollmentWhenDeadlinePassedEvenIfOpen() {
            Klass klass = draftKlass(LocalDate.now().plusDays(1));
            klass.open(LocalDateTime.now());

            assertThatThrownBy(() -> klass.validateEnrollable(LocalDateTime.now().plusDays(2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("마감일");
        }
    }

    @Nested
    @DisplayName("수강료 및 정원 변경")
    class FieldChange {

        @Test
        @DisplayName("DRAFT 상태에서 수강료 변경에 성공한다")
        void shouldChangePriceWhenDraft() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));

            klass.changePrice(BigDecimal.valueOf(5000));

            assertThat(klass.getPrice()).isEqualTo(BigDecimal.valueOf(5000));
        }

        @Test
        @DisplayName("DRAFT 상태에서 최대 정원 변경에 성공한다")
        void shouldChangeMaxCapacityWhenDraft() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));

            klass.changeMaxCapacity(50);

            assertThat(klass.getMaxCapacity()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("정원 상태 확인")
    class CapacityCheck {

        @Test
        @DisplayName("수강 인원이 최대 정원에 도달하면 isFull이 true를 반환한다")
        void shouldReturnTrueWhenEnrolledCountReachesMaxCapacity() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));
            klass.open(LocalDateTime.now());
            for (int i = 0; i < 30; i++) klass.increaseEnrolledCount();

            assertThat(klass.isFull()).isTrue();
        }

        @Test
        @DisplayName("수강 인원이 최대 정원 미만이면 isFull이 false를 반환한다")
        void shouldReturnFalseWhenEnrolledCountBelowMaxCapacity() {
            Klass klass = draftKlass(LocalDate.now().plusDays(5));
            klass.open(LocalDateTime.now());
            for (int i = 0; i < 29; i++) klass.increaseEnrolledCount();

            assertThat(klass.isFull()).isFalse();
        }
    }

    @Nested
    @DisplayName("강의 등록 유효성 검사")
    class ValidationOnCreate {

        @Test
        @DisplayName("가격이 0 미만이면 강의 생성이 거부된다")
        void shouldFailCreationWhenPriceIsNegative() {
            assertThatThrownBy(() -> Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(-1), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("가격");
        }

        @Test
        @DisplayName("최대 정원이 0이면 강의 생성이 거부된다")
        void shouldFailCreationWhenCapacityIsZero() {
            assertThatThrownBy(() -> Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 0,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("정원");
        }

        @Test
        @DisplayName("종료일이 시작일보다 이전이면 강의 생성이 거부된다")
        void shouldFailCreationWhenEndDateBeforeStartDate() {
            assertThatThrownBy(() -> Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(5),
                    LocalDate.now().plusDays(3), 7
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("종료일");
        }

        @Test
        @DisplayName("수강 마감일이 시작일보다 이후이면 강의 생성이 거부된다")
        void shouldFailCreationWhenEnrollmentDeadlineAfterStartDate() {
            assertThatThrownBy(() -> Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(5), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(10), 7
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("마감일");
        }

        @Test
        @DisplayName("취소 가능 기간이 0 미만이면 강의 생성이 거부된다")
        void shouldFailCreationWhenCancellationDeadlineDaysIsNegative() {
            assertThatThrownBy(() -> Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), -1
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("취소");
        }

        @Test
        @DisplayName("취소 가능 기간이 0이면 취소 불가 강의로 생성된다")
        void shouldCreateNonCancellableKlassWhenCancellationDeadlineDaysIsZero() {
            Klass klass = Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 0
            );

            assertThat(klass.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("취소 가능 기간을 지정하지 않으면 기본값 7일로 생성된다")
        void shouldApplyDefaultCancellationDeadlineDaysWhenNotSpecified() {
            Klass klass = Klass.create(
                    CREATOR, "강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5)
            );

            assertThat(klass.getCancellationDeadlineDays())
                    .isEqualTo(EnrollmentPolicy.DEFAULT_CANCELLATION_DEADLINE_DAYS);
        }
    }
}
