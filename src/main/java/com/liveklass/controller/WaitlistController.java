package com.liveklass.controller;

import com.liveklass.dto.common.ApiResponse;
import com.liveklass.dto.waitlist.WaitlistJoinRequest;
import com.liveklass.dto.waitlist.WaitlistResponse;
import com.liveklass.global.constant.ApiHeaders;
import com.liveklass.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waitlists")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WaitlistResponse> join(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @Valid @RequestBody WaitlistJoinRequest request) {
        return ApiResponse.of(WaitlistResponse.from(waitlistService.join(userId, request.klassId())));
    }

    @PostMapping("/{id}/convert")
    public ApiResponse<Void> convert(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @PathVariable Long id) {
        waitlistService.convertToEnrollment(id, userId);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @PathVariable Long id) {
        waitlistService.cancel(id, userId);
        return ApiResponse.of(null);
    }
}
