package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
import com.liveklass.global.exception.waitlist.WaitlistErrorCode;
import com.liveklass.global.exception.waitlist.WaitlistException;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import com.liveklass.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final KlassRepository klassRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryService historyService;
    private final WaitlistNotificationService waitlistNotificationService;
    private final Clock clock;

    @Transactional
    public Waitlist join(Long userId, Long klassId) {
        User user = findUserOrThrow(userId);
        Klass klass = findKlassWithLockOrThrow(klassId);

        validateKlassIsFull(klass);
        validateNoDuplicateActiveWaitlist(userId, klassId);

        int nextPosition = resolveNextWaitlistPosition(klassId);
        return saveWaitlist(user, klass, nextPosition);
    }

    @Transactional
    public void notifyNext(Long klassId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Waitlist waitlist = waitlistRepository
                .findFirstByKlassIdAndStatusOrderByPositionAsc(klassId, WaitlistStatus.WAITING)
                .orElseThrow(() -> new WaitlistException(WaitlistErrorCode.WAITLIST_EMPTY));
        try {
            waitlist.markAsNotified(now);
        } catch (IllegalStateException e) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_STATE_ERROR, e);
        }
    }

    @Transactional
    public void convertToEnrollment(Long waitlistId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Waitlist waitlist = findWaitlistOrThrow(waitlistId);
        validateWaitlistOwner(waitlist, userId);

        markWaitlistAsConverted(waitlist, now);

        Klass klass = findKlassWithLockOrThrow(waitlist.getKlass().getId());
        validateKlassHasCapacity(klass);

        saveEnrollmentFromWaitlist(waitlist, klass, now);
    }

    @Transactional
    public void cancel(Long waitlistId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Waitlist waitlist = findWaitlistOrThrow(waitlistId);
        validateWaitlistOwner(waitlist, userId);
        boolean wasNotified = waitlist.getStatus() == WaitlistStatus.NOTIFIED;
        try {
            waitlist.cancel();
        } catch (IllegalStateException e) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_STATE_ERROR, e);
        }
        if (wasNotified) {
            waitlistNotificationService.notifyNextIfNeeded(waitlist.getKlass().getId(), now);
        }
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new WaitlistException(WaitlistErrorCode.WAITLIST_USER_NOT_FOUND));
    }

    private Klass findKlassWithLockOrThrow(Long klassId) {
        return klassRepository.findByIdWithLock(klassId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_NOT_FOUND));
    }

    private void validateKlassIsFull(Klass klass) {
        if (!klass.isFull()) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_NOT_FULL);
        }
    }

    private void validateKlassHasCapacity(Klass klass) {
        if (klass.isFull()) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_CAPACITY_EXCEEDED);
        }
    }

    private void validateNoDuplicateActiveWaitlist(Long userId, Long klassId) {
        if (waitlistRepository.existsByUserIdAndKlassIdAndStatusIn(
                userId, klassId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED))) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_DUPLICATE);
        }
    }

    private int resolveNextWaitlistPosition(Long klassId) {
        return waitlistRepository.findMaxPositionByKlassId(klassId) + 1;
    }

    private Waitlist saveWaitlist(User user, Klass klass, int position) {
        try {
            Waitlist waitlist = Waitlist.create(user, klass, position);
            return waitlistRepository.save(waitlist);
        } catch (IllegalStateException e) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_STATE_ERROR, e);
        }
    }

    private void validateWaitlistOwner(Waitlist waitlist, Long userId) {
        if (!waitlist.getUser().getId().equals(userId)) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_FORBIDDEN);
        }
    }

    private Waitlist findWaitlistOrThrow(Long waitlistId) {
        return waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new WaitlistException(WaitlistErrorCode.WAITLIST_NOT_FOUND));
    }

    private void markWaitlistAsConverted(Waitlist waitlist, LocalDateTime now) {
        try {
            waitlist.convert(now);
        } catch (IllegalStateException e) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_STATE_ERROR, e);
        }
    }

    private void validateNoDuplicateEnrollment(Long userId, Long klassId) {
        if (enrollmentRepository.existsByUserIdAndKlassIdAndStatusIn(
                userId, klassId, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED))) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_ALREADY_ENROLLED);
        }
    }

    private void saveEnrollmentFromWaitlist(Waitlist waitlist, Klass klass, LocalDateTime now) {
        validateNoDuplicateEnrollment(waitlist.getUser().getId(), klass.getId());
        try {
            Enrollment enrollment = Enrollment.create(waitlist.getUser(), klass, now);
            enrollmentRepository.save(enrollment);
            historyService.recordWaitlistEnroll(enrollment, waitlist.getUser().getId());
            if (klass.isFree()) {
                enrollment.autoConfirmIfFree(now);
                historyService.recordAutoConfirm(enrollment);
            }
        } catch (IllegalStateException e) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_STATE_ERROR, e);
        }
    }
}
