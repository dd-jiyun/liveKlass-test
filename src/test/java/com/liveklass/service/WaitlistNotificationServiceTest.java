package com.liveklass.service;

import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

class WaitlistNotificationServiceTest {

    @Test
    @DisplayName("대기자가 있으면 NOTIFIED로 전환 후 알림을 전송한다")
    void shouldNotifyNextWaitlist() {
        WaitlistRepository repository = Mockito.mock(WaitlistRepository.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        WaitlistNotificationService service = new WaitlistNotificationService(repository, notificationService);

        Waitlist waitlist = Mockito.mock(Waitlist.class);
        given(repository.findFirstForNotificationByKlassIdAndStatus(1L, WaitlistStatus.WAITING))
                .willReturn(Optional.of(waitlist));

        service.notifyNextIfNeeded(1L, LocalDateTime.now());

        then(waitlist).should().markAsNotified(any(LocalDateTime.class));
        then(notificationService).should().notifyWaiter(waitlist);
    }

    @Test
    @DisplayName("대기자가 없으면 알림을 전송하지 않는다")
    void shouldNotNotifyWhenNoWaiter() {
        WaitlistRepository repository = Mockito.mock(WaitlistRepository.class);
        NotificationService notificationService = Mockito.mock(NotificationService.class);
        WaitlistNotificationService service = new WaitlistNotificationService(repository, notificationService);

        given(repository.findFirstForNotificationByKlassIdAndStatus(1L, WaitlistStatus.WAITING))
                .willReturn(Optional.empty());

        service.notifyNextIfNeeded(1L, LocalDateTime.now());

        then(notificationService).shouldHaveNoInteractions();
    }
}
