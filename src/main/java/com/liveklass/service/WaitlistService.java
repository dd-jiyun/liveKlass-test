package com.liveklass.service;

import com.liveklass.domain.enrollment.ChangedBy;
import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.enrollment.HistoryReason;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
import com.liveklass.global.exception.waitlist.WaitlistErrorCode;
import com.liveklass.global.exception.waitlist.WaitlistException;
import com.liveklass.repository.EnrollmentHistoryRepository;
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
    private final EnrollmentHistoryRepository historyRepository;
    private final Clock clock;

    @Transactional
    public Waitlist join(Long userId, Long klassId) {
        User user = findUserOrThrow(userId);
        Klass klass = findKlassOrThrow(klassId);

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
        saveConfirmedEnrollmentFromWaitlist(waitlist, now);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new WaitlistException(WaitlistErrorCode.WAITLIST_USER_NOT_FOUND));
    }

    private Klass findKlassOrThrow(Long klassId) {
        return klassRepository.findById(klassId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_NOT_FOUND));
    }

    private void validateKlassIsFull(Klass klass) {
        if (!klass.isFull()) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_NOT_FULL);
        }
    }

    private void validateNoDuplicateActiveWaitlist(Long userId, Long klassId) {
        if (waitlistRepository.existsByUserIdAndKlassIdAndStatusIn(
                userId, klassId, List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED))) {
            throw new WaitlistException(WaitlistErrorCode.WAITLIST_DUPLICATE);
        }
    }

    private int resolveNextWaitlistPosition(Long klassId) {
        return waitlistRepository.countByKlassId(klassId) + 1;
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

    private void saveConfirmedEnrollmentFromWaitlist(Waitlist waitlist, LocalDateTime now) {
        Enrollment enrollment = Enrollment.createConfirmed(waitlist.getUser(), waitlist.getKlass(), now);
        enrollmentRepository.save(enrollment);
        historyRepository.save(EnrollmentHistory.record(
                enrollment, null, EnrollmentStatus.CONFIRMED,
                HistoryReason.WAITLIST_CONVERTED, ChangedBy.USER, waitlist.getUser().getId()));
    }
}
