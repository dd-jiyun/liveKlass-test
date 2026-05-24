package com.liveklass.service;

import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.repository.WaitlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class WaitlistNotificationService {

    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;

    public WaitlistNotificationService(WaitlistRepository waitlistRepository,
                                       NotificationService notificationService) {
        this.waitlistRepository = waitlistRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void notifyNextIfNeeded(Long klassId, LocalDateTime now) {
        // JOIN FETCH로 user·klass를 트랜잭션 내에 미리 초기화 — afterCommit에서 LazyInitializationException 방지
        waitlistRepository.findFirstForNotificationByKlassIdAndStatus(klassId, WaitlistStatus.WAITING)
                .ifPresent(waitlist -> {
                    waitlist.markAsNotified(now);
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                notificationService.notifyWaiter(waitlist);
                            }
                        });
                    } else {
                        notificationService.notifyWaiter(waitlist);
                    }
                });
    }
}
