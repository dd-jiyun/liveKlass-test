package com.liveklass.repository;

import com.liveklass.domain.enrollment.EnrollmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentHistoryRepository extends JpaRepository<EnrollmentHistory, Long> {

    List<EnrollmentHistory> findByEnrollmentId(Long enrollmentId);
}
