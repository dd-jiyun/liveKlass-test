package com.liveklass.domain.enrollment;

import com.liveklass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "enrollment_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnrollmentHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangedBy changedBy;

    @Column(name = "user_id")
    private Long userId;

    public static EnrollmentHistory record(Enrollment enrollment, EnrollmentStatus fromStatus,
                                           EnrollmentStatus toStatus, HistoryReason reason,
                                           ChangedBy changedBy, Long userId) {
        EnrollmentHistory history = new EnrollmentHistory();
        history.enrollment = enrollment;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.reason = reason;
        history.changedBy = changedBy;
        history.userId = userId;
        return history;
    }
}
