package com.liveklass.service;

import com.liveklass.domain.waitlist.Waitlist;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockNotificationService implements NotificationService {

    @Override
    public void notifyWaiter(Waitlist waitlist) {
        log.info("[알림 mock] 대기자 userId={} 에게 수강 기회 알림 전송 (klassId={})",
                waitlist.getUser().getId(), waitlist.getKlass().getId());
    }
}
