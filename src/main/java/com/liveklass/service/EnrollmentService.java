package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.user.User;
import com.liveklass.global.exception.enrollment.EnrollmentErrorCode;
import com.liveklass.global.exception.enrollment.EnrollmentException;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
import com.liveklass.domain.waitlist.WaitlistStatus;
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
    private final KlassRepository klassRepository;
    private final UserRepository userRepository;
    private final WaitlistRepository waitlistRepository;
    private final Clock clock;

    private final EnrollmentHistoryService historyService;
    private final WaitlistNotificationService waitlistNotificationService;

    @Transactional
    public Enrollment enroll(Long userId, Long klassId) {
        LocalDateTime now = LocalDateTime.now(clock);
        User user = findUserOrThrow(userId);
        Klass klass = findKlassWithLockOrThrow(klassId);

        validateNoDuplicateActiveEnrollment(userId, klassId);
        validateKlassHasCapacity(klass);
        validateNoNotifiedWaiter(klassId);

        Enrollment enrollment = savePendingEnrollment(user, klass, now);

        historyService.recordEnroll(enrollment, userId);

        if (klass.isFree()) {
            enrollment.autoConfirmIfFree(now);
            historyService.recordAutoConfirm(enrollment);
        }

        return enrollment;
    }

    @Transactional
    public void confirm(Long enrollmentId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Enrollment enrollment = findEnrollmentOrThrow(enrollmentId);
        validateEnrollmentOwner(enrollment, userId);
        EnrollmentStatus before = enrollment.getStatus();
        transitionToConfirmed(enrollment, now);
        historyService.recordConfirm(enrollment, before, userId);
    }

    @Transactional
    public void cancel(Long enrollmentId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Enrollment enrollment = findEnrollmentOrThrow(enrollmentId);
        validateEnrollmentOwner(enrollment, userId);
        EnrollmentStatus before = enrollment.getStatus();
        transitionToCancelled(enrollment, now);
        historyService.recordCancel(enrollment, before, userId);

        waitlistNotificationService.notifyNextIfNeeded(enrollment.getKlass().getId(), now);
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

    private void validateNoNotifiedWaiter(Long klassId) {
        if (waitlistRepository.existsByKlassIdAndStatus(klassId, WaitlistStatus.NOTIFIED)) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_WAITLIST_PRIORITY);
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
            return enrollment;
        } catch (IllegalStateException e) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_STATE_ERROR, e);
        }
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


    private Enrollment findEnrollmentOrThrow(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_NOT_FOUND));
    }

    private void validateEnrollmentOwner(Enrollment enrollment, Long userId) {
        if (!enrollment.getUser().getId().equals(userId)) {
            throw new EnrollmentException(EnrollmentErrorCode.ENROLLMENT_FORBIDDEN);
        }
    }
}
