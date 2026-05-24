package com.liveklass.controller;

import com.liveklass.dto.enrollment.EnrollmentCreateRequest;
import com.liveklass.dto.enrollment.EnrollmentResponse;
import com.liveklass.dto.common.ApiResponse;
import com.liveklass.global.constant.ApiHeaders;
import com.liveklass.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EnrollmentResponse> enroll(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @Valid @RequestBody EnrollmentCreateRequest request) {
        return ApiResponse.of(EnrollmentResponse.from(enrollmentService.enroll(userId, request.klassId())));
    }

    @GetMapping("/me")
    public ApiResponse<List<EnrollmentResponse>> myEnrollments(@RequestHeader(ApiHeaders.USER_ID) Long userId) {
        return ApiResponse.of(enrollmentService.findByUser(userId).stream()
                .map(EnrollmentResponse::from)
                .toList());
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @PathVariable Long id) {
        enrollmentService.confirm(id, userId);
        return ApiResponse.of(null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @PathVariable Long id) {
        enrollmentService.cancel(id, userId);
        return ApiResponse.of(null);
    }
}
