package com.liveklass.dto.waitlist;

import com.liveklass.domain.waitlist.Waitlist;

import java.time.LocalDateTime;

public record WaitlistResponse(
        Long id,
        Long klassId,
        String klassTitle,
        String status,
        int position,
        LocalDateTime notifiedAt,
        LocalDateTime joinedAt
) {
    public static WaitlistResponse from(Waitlist waitlist) {
        return new WaitlistResponse(
                waitlist.getId(),
                waitlist.getKlass().getId(),
                waitlist.getKlass().getTitle(),
                waitlist.getStatus().name(),
                waitlist.getPosition(),
                waitlist.getNotifiedAt(),
                waitlist.getCreatedAt()
        );
    }
}
