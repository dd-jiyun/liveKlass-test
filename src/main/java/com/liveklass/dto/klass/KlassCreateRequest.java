package com.liveklass.dto.klass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record KlassCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull BigDecimal price,
        @NotNull @Positive Integer maxCapacity,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalDate enrollmentDeadline,
        Integer cancellationDeadlineDays
) {}
