package com.liveklass.repository;

import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    Optional<Waitlist> findFirstByKlassIdAndStatusOrderByPositionAsc(Long klassId, WaitlistStatus status);

    @Query("SELECT w FROM Waitlist w JOIN FETCH w.user JOIN FETCH w.klass WHERE w.klass.id = :klassId AND w.status = :status ORDER BY w.position ASC")
    Optional<Waitlist> findFirstForNotificationByKlassIdAndStatus(@Param("klassId") Long klassId, @Param("status") WaitlistStatus status);

    @Query("SELECT COALESCE(MAX(w.position), 0) FROM Waitlist w WHERE w.klass.id = :klassId")
    int findMaxPositionByKlassId(@Param("klassId") Long klassId);

    Optional<Waitlist> findByUserIdAndKlassIdAndStatus(Long userId, Long klassId, WaitlistStatus status);

    boolean existsByUserIdAndKlassIdAndStatusIn(Long userId, Long klassId, List<WaitlistStatus> statuses);

    List<Waitlist> findByStatus(WaitlistStatus status);

    List<Waitlist> findByKlassIdAndStatusIn(Long klassId, List<WaitlistStatus> statuses);

    boolean existsByKlassIdAndStatus(Long klassId, WaitlistStatus status);
}
