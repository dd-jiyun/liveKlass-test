package com.liveklass.service;

import com.liveklass.domain.enrollment.ChangedBy;
import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.domain.enrollment.HistoryReason;
import com.liveklass.repository.EnrollmentHistoryRepository;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentHistoryService {

    private final EnrollmentHistoryRepository historyRepository;

    public EnrollmentHistoryService(EnrollmentHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void recordConfirm(Enrollment enrollment, EnrollmentStatus before, Long userId) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, before, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.USER, userId));
    }

    public void recordCancel(Enrollment enrollment, EnrollmentStatus before, Long userId) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, before, EnrollmentStatus.CANCELLED,
                HistoryReason.USER_CANCEL, ChangedBy.USER, userId));
    }

    public void recordEnroll(Enrollment enrollment, Long userId) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, null, EnrollmentStatus.PENDING,
                HistoryReason.USER_ENROLL, ChangedBy.USER, userId));
    }

    public void recordAutoConfirm(Enrollment enrollment) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED,
                HistoryReason.PAYMENT_CONFIRMED, ChangedBy.SYSTEM, null));
    }

    public void recordWaitlistEnroll(Enrollment enrollment, Long userId) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, null, EnrollmentStatus.PENDING,
                HistoryReason.WAITLIST_CONVERTED, ChangedBy.USER, userId));
    }

    public void recordPendingExpire(Enrollment enrollment) {
        historyRepository.save(EnrollmentHistory.record(
                enrollment, EnrollmentStatus.PENDING, EnrollmentStatus.CANCELLED,
                HistoryReason.PENDING_EXPIRED, ChangedBy.SYSTEM, null));
    }
}

