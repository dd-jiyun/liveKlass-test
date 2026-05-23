package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.domain.user.User;
import com.liveklass.global.exception.klass.KlassErrorCode;
import com.liveklass.global.exception.klass.KlassException;
import com.liveklass.repository.EnrollmentRepository;
import com.liveklass.repository.KlassRepository;
import com.liveklass.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Transactional
    public Klass create(Long creatorId, String title, String description, BigDecimal price,
                             int maxCapacity, LocalDate startDate, LocalDate endDate,
                             LocalDate enrollmentDeadline, int cancellationDeadlineDays) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new KlassException(KlassErrorCode.KLASS_USER_NOT_FOUND));
        Klass klass = Klass.create(creator, title, description, price, maxCapacity,
                startDate, endDate, enrollmentDeadline, cancellationDeadlineDays);
        return klassRepository.save(klass);
    }

    @Transactional
    public Klass update(Long klassId, Long creatorId,
                             String title, String description, BigDecimal price,
                             Integer maxCapacity, LocalDate startDate, LocalDate endDate,
                             LocalDate enrollmentDeadline, Integer cancellationDeadlineDays) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.update(title, description, price, maxCapacity, startDate, endDate, enrollmentDeadline, cancellationDeadlineDays);
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        return klass;
    }

    @Transactional
    public void delete(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.validateDeletable();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
        klassRepository.delete(klass);
    }

    @Transactional
    public void open(Long klassId, Long creatorId, LocalDateTime now) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.open(now);
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
    }

    @Transactional
    public void close(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.close();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
    }

    @Transactional
    public void reopen(Long klassId, Long creatorId) {
        Klass klass = findKlass(klassId);
        validateCreator(klass, creatorId);
        try {
            klass.reopen();
        } catch (IllegalStateException e) {
            throw new KlassException(KlassErrorCode.KLASS_STATE_ERROR, e);
        }
    }

    public Klass findById(Long klassId) {
        return findKlass(klassId);
    }

    public List<Klass> findAll(KlassStatus status) {
        if (status == null) {
            return klassRepository.findAll();
        }
        return klassRepository.findByStatus(status);
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
}
