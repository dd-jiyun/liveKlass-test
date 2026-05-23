package com.liveklass.service;

import com.liveklass.domain.waitlist.Waitlist;

public interface NotificationService {

    void notifyWaiter(Waitlist waitlist);
}
