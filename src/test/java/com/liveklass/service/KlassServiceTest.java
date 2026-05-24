package com.liveklass.service;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
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
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(KlassServiceTest.TestClockConfig.class)
@Transactional
class KlassServiceTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return Clock.fixed(Instant.now(), ZoneId.systemDefault());
        }
    }

    @Autowired
    KlassService klassService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    KlassRepository klassRepository;

    User creator;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(User.create("크리에이터", "creator@test.com", UserRole.CREATOR));
    }

    private Klass savedDraftKlass() {
        Klass klass = Klass.create(creator, "스프링 강의", "설명", BigDecimal.valueOf(10000),
                30, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        return klassRepository.save(klass);
    }

    @Nested
    @DisplayName("강의 생성")
    class CreateKlass {

        @Test
        @DisplayName("강의를 생성하면 DRAFT 상태로 저장된다")
        void shouldSaveKlassAsDraftWhenCreated() {
            Klass klass = klassService.create(
                    creator.getId(), "스프링 강의", "설명", BigDecimal.valueOf(10000), 30,
                    LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);

            assertThat(klass.getId()).isNotNull();
            assertThat(klass.getStatus()).isEqualTo(KlassStatus.DRAFT);
            assertThat(klass.getTitle()).isEqualTo("스프링 강의");
        }
    }

    @Nested
    @DisplayName("강의 상태 전이")
    class KlassStatusTransition {

        @Test
        @DisplayName("DRAFT 강의를 OPEN으로 전환하면 상태가 OPEN이 된다")
        void shouldTransitionKlassFromDraftToOpen() {
            Klass klass = savedDraftKlass();

            klassService.open(klass.getId(), creator.getId());

            assertThat(klassRepository.findById(klass.getId()).orElseThrow().getStatus())
                    .isEqualTo(KlassStatus.OPEN);
        }

        @Test
        @DisplayName("OPEN 강의를 CLOSED로 전환하면 상태가 CLOSED가 된다")
        void shouldTransitionKlassFromOpenToClosed() {
            Klass klass = savedDraftKlass();
            klassService.open(klass.getId(), creator.getId());

            klassService.close(klass.getId(), creator.getId());

            assertThat(klassRepository.findById(klass.getId()).orElseThrow().getStatus())
                    .isEqualTo(KlassStatus.CLOSED);
        }
    }

    @Nested
    @DisplayName("강의 조회")
    class GetKlass {

        @Test
        @DisplayName("강의 ID로 단건을 조회할 수 있다")
        void shouldReturnKlassById() {
            Klass klass = savedDraftKlass();

            Klass found = klassService.findById(klass.getId());

            assertThat(found.getId()).isEqualTo(klass.getId());
        }

        @Test
        @DisplayName("상태로 강의 목록을 필터링해서 조회할 수 있다")
        void shouldListKlassesByStatus() {
            Klass draft = savedDraftKlass();
            klassService.open(draft.getId(), creator.getId());

            Klass anotherDraft = savedDraftKlass();

            List<Klass> openKlasses = klassService.findAll(creator.getId(), KlassStatus.OPEN);
            List<Klass> draftKlasses = klassService.findAll(creator.getId(), KlassStatus.DRAFT);

            assertThat(openKlasses).hasSize(1);
            assertThat(draftKlasses).hasSize(1);
        }

        @Test
        @DisplayName("status가 null이면 전체 강의를 조회한다")
        void shouldReturnAllKlassesWhenStatusIsNull() {
            savedDraftKlass();
            savedDraftKlass();

            List<Klass> all = klassService.findAll(creator.getId(), null);

            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("CREATOR가 status=OPEN으로 조회하면 다른 크리에이터의 OPEN 강의도 포함된다")
        void shouldReturnAllOpenKlassesForCreatorWhenStatusIsOpen() {
            User otherCreator = userRepository.save(User.create("다른크리에이터", "other@test.com", UserRole.CREATOR));
            Klass ownOpen = savedDraftKlass();
            klassService.open(ownOpen.getId(), creator.getId());

            Klass otherOpen = Klass.create(otherCreator, "다른 강의", "설명", BigDecimal.valueOf(5000),
                    10, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);
            otherOpen.open(java.time.LocalDateTime.now());
            klassRepository.save(otherOpen);

            List<Klass> result = klassService.findAll(creator.getId(), KlassStatus.OPEN);

            assertThat(result).extracting(Klass::getId)
                    .contains(ownOpen.getId(), otherOpen.getId());
        }

        @Test
        @DisplayName("CREATOR가 status를 지정하지 않으면 자신의 강의만 조회된다")
        void shouldReturnOnlyOwnKlassesForCreatorWhenStatusIsNull() {
            User otherCreator = userRepository.save(User.create("다른크리에이터", "other2@test.com", UserRole.CREATOR));
            Klass ownDraft = savedDraftKlass();
            Klass otherDraft = Klass.create(otherCreator, "다른 강의", "설명", BigDecimal.valueOf(5000),
                    10, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                    LocalDate.now().plusDays(5), 7);
            klassRepository.save(otherDraft);

            List<Klass> result = klassService.findAll(creator.getId(), null);

            assertThat(result).extracting(Klass::getId).contains(ownDraft.getId());
            assertThat(result).extracting(Klass::getId).doesNotContain(otherDraft.getId());
        }
    }

    @Nested
    @DisplayName("역할 승격")
    class PromoteUser {

        @Test
        @DisplayName("STUDENT가 promoteToCreator를 호출하면 CREATOR로 승격된다")
        void shouldPromoteStudentToCreator() {
            User student = userRepository.save(User.create("수강생", "student@test.com", UserRole.STUDENT));
            student.promoteToCreator();

            assertThat(student.getRole()).isEqualTo(UserRole.CREATOR);
        }
    }
}
