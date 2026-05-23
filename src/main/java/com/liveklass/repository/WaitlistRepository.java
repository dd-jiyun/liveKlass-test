package com.liveklass.repository;

import com.liveklass.domain.waitlist.Waitlist;
import com.liveklass.domain.waitlist.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    int countByKlassId(Long klassId);

    Optional<Waitlist> findFirstByKlassIdAndStatusOrderByPositionAsc(Long klassId, WaitlistStatus status);

    boolean existsByUserIdAndKlassIdAndStatusIn(Long userId, Long klassId, List<WaitlistStatus> statuses);
}
