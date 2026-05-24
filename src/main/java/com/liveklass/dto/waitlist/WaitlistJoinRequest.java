package com.liveklass.dto.waitlist;

import jakarta.validation.constraints.NotNull;

public record WaitlistJoinRequest(@NotNull Long klassId) {}
