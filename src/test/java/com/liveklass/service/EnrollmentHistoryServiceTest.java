package com.liveklass.service;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentHistory;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import com.liveklass.repository.EnrollmentHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.BDDMockito.then;

class EnrollmentHistoryServiceTest {

    @Test
    @DisplayName("결제 확정 이력을 기록한다")
    void shouldRecordConfirmHistory() {
        EnrollmentHistoryRepository repository = Mockito.mock(EnrollmentHistoryRepository.class);
        EnrollmentHistoryService service = new EnrollmentHistoryService(repository);

        Enrollment enrollment = Mockito.mock(Enrollment.class);
        service.recordConfirm(enrollment, EnrollmentStatus.PENDING, 1L);

        then(repository).should().save(Mockito.any(EnrollmentHistory.class));
    }
}

