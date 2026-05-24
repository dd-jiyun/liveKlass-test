package com.liveklass.service;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConcurrencyTest {

    @Autowired
    EnrollmentService enrollmentService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    KlassRepository klassRepository;
    @Autowired
    Clock clock;

    @Test
    @DisplayName("정원 1명 강의에 N개 동시 신청 시 1건만 성공한다")
    void shouldAllowOnlyOneEnrollmentWhenCapacityIsOne() throws InterruptedException {
        User creator = userRepository.save(User.create("크리에이터", uniqueEmail(), UserRole.CREATOR));
        Klass klass = Klass.create(creator, "동시성 테스트 강의", "설명", BigDecimal.valueOf(10000),
                1, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        klass.open(LocalDateTime.now(clock));
        klassRepository.save(klass);

        int threadCount = 10;
        List<User> students = createStudents(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (User student : students) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(student.getId(), klass.getId());
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 신청 후 enrolledCount가 maxCapacity를 초과하지 않는다")
    void shouldNotExceedCapacityUnderConcurrentRequests() throws InterruptedException {
        int maxCapacity = 3;
        int threadCount = 20;

        User creator = userRepository.save(User.create("크리에이터", uniqueEmail(), UserRole.CREATOR));
        Klass klass = Klass.create(creator, "동시성 정합성 테스트", "설명", BigDecimal.valueOf(10000),
                maxCapacity, LocalDate.now().plusDays(10), LocalDate.now().plusDays(40),
                LocalDate.now().plusDays(5), 7);
        klass.open(LocalDateTime.now(clock));
        klassRepository.save(klass);

        List<User> students = createStudents(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (User student : students) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(student.getId(), klass.getId());
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        Klass result = klassRepository.findById(klass.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(maxCapacity);
        assertThat(result.getEnrolledCount()).isEqualTo(maxCapacity);
    }

    private List<User> createStudents(int count) {
        List<User> students = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            students.add(userRepository.save(User.create("학생" + i, uniqueEmail(), UserRole.STUDENT)));
        }
        return students;
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@test.com";
    }
}
