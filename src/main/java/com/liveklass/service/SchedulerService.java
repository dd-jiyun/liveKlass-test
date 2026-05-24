package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final KlassRepository klassRepository;
    private final EnrollmentHistoryService historyService;
    private final WaitlistNotificationService waitlistNotificationService;
    private final Clock clock;

    /**
     * PENDING 상태로 20분 경과한 수강 신청을 자동 취소하고 대기자에게 알림을 보낸다.
     */
    @Scheduled(fixedDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS, initialDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS)
    @Transactional
    public void expirePendingEnrollments() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Enrollment> expired = enrollmentRepository.findByStatusAndPendingExpiresAtBefore(
                EnrollmentStatus.PENDING, now);

        Set<Long> affectedKlassIds = new LinkedHashSet<>();
        for (Enrollment enrollment : expired) {
            enrollment.expireIfOverdue(now);
            historyService.recordPendingExpire(enrollment);
            affectedKlassIds.add(enrollment.getKlass().getId());
            log.info("enrollment {} 자동 만료: PENDING → CANCELLED", enrollment.getId());
            waitlistRepository.findByUserIdAndKlassIdAndStatus(
                            enrollment.getUser().getId(), enrollment.getKlass().getId(), WaitlistStatus.CONVERTED)
                    .ifPresent(w -> log.warn(
                            "waitlist {} (userId={}, klassId={}) CONVERTED 상태 — PENDING 만료로 결제 기회 소멸. 사용자 재대기/재신청 필요",
                            w.getId(), w.getUser().getId(), enrollment.getKlass().getId()));
        }

        for (Long klassId : affectedKlassIds) {
            waitlistNotificationService.notifyNextIfNeeded(klassId, now);
        }
    }

    /**
     * NOTIFIED 상태로 수락 기간이 지난 대기 항목을 EXPIRED로 전환하고 다음 대기자에게 알림을 보낸다.
     */
    @Scheduled(fixedDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS, initialDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS)
    @Transactional
    public void expireNotifiedWaitlists() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Waitlist> notifiedEntries = waitlistRepository.findByStatus(WaitlistStatus.NOTIFIED);

        Set<Long> affectedKlassIds = new LinkedHashSet<>();
        for (Waitlist waitlist : notifiedEntries) {
            WaitlistStatus before = waitlist.getStatus();
            waitlist.expireIfOverdue(now);
            if (waitlist.getStatus() != before) {
                affectedKlassIds.add(waitlist.getKlass().getId());
                log.info("waitlist {} 자동 만료: NOTIFIED → EXPIRED", waitlist.getId());
            }
        }

        for (Long klassId : affectedKlassIds) {
            waitlistNotificationService.notifyNextIfNeeded(klassId, now);
        }
    }

    /**
     * 신청 마감일이 지난 OPEN 상태 강의를 자동으로 CLOSED로 전환한다.
     */
    @Scheduled(fixedDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS, initialDelay = EnrollmentPolicy.SCHEDULER_INTERVAL_MILLIS)
    @Transactional
    public void closeExpiredKlasses() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Klass> openKlasses = klassRepository.findByStatus(KlassStatus.OPEN);

        List<WaitlistStatus> activeStatuses = List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED);
        for (Klass klass : openKlasses) {
            KlassStatus before = klass.getStatus();
            klass.closeIfDeadlineReached(now);
            if (klass.getStatus() != before) {
                log.info("klass {} 자동 마감: 신청 마감일 경과", klass.getId());
                // 강의가 CLOSED되므로 WAITING·NOTIFIED 전원 취소. notifyNextIfNeeded는 의도적으로 호출하지 않음
                // — 강의가 종료된 상태에서 다음 대기자를 승격시킬 슬롯이 없음
                for (Waitlist w : waitlistRepository.findByKlassIdAndStatusIn(klass.getId(), activeStatuses)) {
                    boolean wasNotified = w.getStatus() == WaitlistStatus.NOTIFIED;
                    w.cancel();
                    if (wasNotified) {
                        log.info("klass {} 마감: NOTIFIED 대기자 {} 취소 — 슬롯 없음(강의 종료)", klass.getId(), w.getId());
                    }
                }
            }
        }
    }
}
