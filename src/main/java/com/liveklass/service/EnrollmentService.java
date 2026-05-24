package com.liveklass.service;

import com.liveklass.domain.enrollment.ChangedBy;
import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.enrollment.HistoryReason;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.global.exception.enrollment.EnrollmentErrorCode;
import com.liveklass.global.exception.enrollment.EnrollmentException;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
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
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryRepository historyRepository;
    private final KlassRepository klassRepository;
    private final UserRepository userRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public Enrollment enroll(Long userId, Long klassId) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = findUserOrThrow(userId);
        Klass klass = findKlassWithLockOrThrow(klassId);

        validateNoDuplicateActiveEnrollment(userId, klassId);
        validateKlassHasCapacity(klass);

        Enrollment enrollment = savePendingEnrollment(user, klass, now);

        if (klass.isFree()) {
            autoConfirmFreeEnrollment(enrollment, now);
        }

        return enrollment;
    }

    @Transactional
    public void confirm(Long enrollmentId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Enrollment enrollment = findEnrollmentOrThrow(enrollmentId);
        EnrollmentStatus before = enrollment.getStatus();
        transitionToConfirmed(enrollment, now);
        historyRepository.save(EnrollmentHistory.record(
                enrollment, before, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.USER, userId));
    }

    @Transactional
    public void cancel(Long enrollmentId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Enrollment enrollment = findEnrollmentOrThrow(enrollmentId);
        EnrollmentStatus before = enrollment.getStatus();
        transitionToCancelled(enrollment, now);
        historyRepository.save(EnrollmentHistory.record(
                enrollment, before, EnrollmentStatus.CANCELLED,
                HistoryReason.USER_CANCEL, ChangedBy.USER, userId));

        notifyNextWaitlistIfExists(enrollment.getKlass().getId(), now);
    }

    public List<Enrollment> findByUser(Long userId) {
        return enrollmentRepository.findByUserId(userId);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_USER_NOT_FOUND));
    }

    private Klass findKlassWithLockOrThrow(Long klassId) {
        return klassRepository.findByIdWithLock(klassId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_NOT_FOUND));
    }

    private void validateKlassHasCapacity(Klass klass) {
        if (klass.isFull()) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_CAPACITY_EXCEEDED);
        }
    }

    private void validateNoDuplicateActiveEnrollment(Long userId, Long klassId) {
        if (enrollmentRepository.existsByUserIdAndKlassIdAndStatusIn(
                userId, klassId, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED))) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_DUPLICATE);
        }
    }

    private Enrollment savePendingEnrollment(User user, Klass klass, LocalDateTime now) {
        try {
            Enrollment enrollment = Enrollment.create(user, klass, now);
            enrollmentRepository.save(enrollment);
            historyRepository.save(EnrollmentHistory.record(
                    enrollment, null, EnrollmentStatus.PENDING,
                    HistoryReason.USER_ENROLL, ChangedBy.USER, user.getId()));
            return enrollment;
        } catch (IllegalStateException e) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_STATE_ERROR, e);
        }
    }

    private void autoConfirmFreeEnrollment(Enrollment enrollment, LocalDateTime now) {
        enrollment.autoConfirmIfFree(now);
        historyRepository.save(EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.SYSTEM, null));
    }

    private void transitionToConfirmed(Enrollment enrollment, LocalDateTime now) {
        try {
            enrollment.confirm(now);
        } catch (IllegalStateException e) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_STATE_ERROR, e);
        }
    }

    private void transitionToCancelled(Enrollment enrollment, LocalDateTime now) {
        try {
            enrollment.cancel(now);
        } catch (IllegalStateException e) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_STATE_ERROR, e);
        }
    }

    private void notifyNextWaitlistIfExists(Long klassId, LocalDateTime now) {
        waitlistRepository.findFirstByKlassIdAndStatusOrderByPositionAsc(klassId, WaitlistStatus.WAITING)
                .ifPresent(waitlist -> {
                    waitlist.markAsNotified(now);
                    notificationService.notifyWaiter(waitlist);
                });
    }

    private Enrollment findEnrollmentOrThrow(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_NOT_FOUND));
    }
}
