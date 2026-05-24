package com.liveklass.dto.klass;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KlassPatchRequest(
        String title,
        String description,
        BigDecimal price,
        Integer maxCapacity,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate enrollmentDeadline,
        Integer cancellationDeadlineDays
) {
}

