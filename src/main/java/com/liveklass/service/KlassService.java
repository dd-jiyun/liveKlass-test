package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentPolicy;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.domain.user.User;
import com.liveklass.domain.user.UserRole;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import com.liveklass.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class KlassService {

    private final KlassRepository klassRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final Clock clock;

    @Transactional
    public Klass create(Long creatorId, String title, String description, BigDecimal price,
                        int maxCapacity, LocalDate startDate, LocalDate endDate,
                        LocalDate enrollmentDeadline, Integer cancellationDeadlineDays) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_USER_NOT_FOUND));
        int effectiveDays = getEffectiveDays(cancellationDeadlineDays);
        Klass klass = Klass.create(creator, title, description, price, maxCapacity,
                startDate, endDate, enrollmentDeadline, effectiveDays);
        return klassRepository.save(klass);
    }

    private int getEffectiveDays(Integer cancellationDeadlineDays) {
        return cancellationDeadlineDays != null
                ? cancellationDeadlineDays
                : EnrollmentPolicy.DEFAULT_CANCELLATION_DEADLINE_DAYS;
    }

    @Transactional
    public Klass update(Long id, Long creatorId,
                        String title, String description, BigDecimal price,
                        Integer maxCapacity, LocalDate startDate, LocalDate endDate,
                        LocalDate enrollmentDeadline, Integer cancellationDeadlineDays) {
        Klass klass = findKlass(id);
        validateCreator(klass, creatorId);
        try {
            klass.update(title, description, price, maxCapacity, startDate, endDate, enrollmentDeadline, cancellationDeadlineDays);
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        return klass;
    }

    @Transactional
    public void delete(Long id, Long creatorId) {
        Klass klass = findKlass(id);
        validateCreator(klass, creatorId);
        try {
            klass.validateDeletable();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        klassRepository.delete(klass);
    }

    @Transactional
    public Klass open(Long klassId, Long creatorId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.open(now);
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        return klass;
    }

    @Transactional
    public Klass close(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.close();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        cancelPendingWaitlistEntries(klassId);
        return klass;
    }

    @Transactional
    public Klass reopen(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.reopen();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        return klass;
    }

    public Klass findById(Long klassId) {
        return findKlass(klassId);
    }

    public List<Klass> findAll(Long userId, KlassStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_USER_NOT_FOUND));

        if (user.getRole() == UserRole.STUDENT) {
            return klassRepository.findByStatus(KlassStatus.OPEN);
        }

        return status == null
                ? klassRepository.findByCreatorId(userId)
                : klassRepository.findByCreatorIdAndStatus(userId, status);
    }

    public List<Enrollment> listStudents(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        return enrollmentRepository.findByKlassIdAndStatus(klassId, EnrollmentStatus.CONFIRMED);
    }

    private Klass findKlass(Long klassId) {
        return klassRepository.findById(klassId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_NOT_FOUND));
    }

    private void validateCreator(Klass klass, Long creatorId) {
        if (!klass.getCreator().getId().equals(creatorId)) {
            throw new KlassException(KlassErrorCode.KLASS_FORBIDDEN);
        }
    }

    private void cancelPendingWaitlistEntries(Long klassId) {
        List<WaitlistStatus> activeStatuses = List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED);
        waitlistRepository.findByKlassIdAndStatusIn(klassId, activeStatuses)
                .forEach(Waitlist::cancel);
    }
}
