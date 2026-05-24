package com.liveklass.repository;

import com.liveklass.domain.enrollment.Enrollment;
import com.liveklass.domain.enrollment.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByUserId(Long userId);

    List<Enrollment> findByKlassIdAndStatus(Long klassId, EnrollmentStatus status);

    boolean existsByUserIdAndKlassIdAndStatusIn(Long userId, Long klassId, List<EnrollmentStatus> statuses);

    List<Enrollment> findByStatusAndPendingExpiresAtBefore(EnrollmentStatus status, LocalDateTime threshold);
}
