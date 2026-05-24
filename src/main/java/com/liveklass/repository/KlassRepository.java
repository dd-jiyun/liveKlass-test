package com.liveklass.repository;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KlassRepository extends JpaRepository<Klass, Long> {

    List<Klass> findByStatus(KlassStatus status);

    List<Klass> findByCreatorId(Long creatorId);

    List<Klass> findByCreatorIdAndStatus(Long creatorId, KlassStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from Klass k where k.id = :id")
    Optional<Klass> findByIdWithLock(@Param("id") Long id);
}
