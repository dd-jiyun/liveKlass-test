package com.liveklass.dto.enrollment;

import jakarta.validation.constraints.NotNull;

public record EnrollmentCreateRequest(@NotNull Long klassId) {}
