package com.liveklass.controller;

import com.liveklass.domain.klass.Klass;
import com.liveklass.domain.klass.KlassStatus;
import com.liveklass.dto.klass.KlassCreateRequest;
import com.liveklass.dto.klass.KlassResponse;
import com.liveklass.dto.klass.KlassPatchRequest;
import com.liveklass.dto.klass.StudentResponse;
import com.liveklass.dto.common.ApiResponse;
import com.liveklass.global.constant.ApiHeaders;
import com.liveklass.service.KlassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/klasses")
@RequiredArgsConstructor
public class KlassController {

    private final KlassService klassService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KlassResponse> create(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @Valid @RequestBody KlassCreateRequest request) {
        Klass klass = klassService.create(
                creatorId, request.title(), request.description(), request.price(),
                request.maxCapacity(), request.startDate(), request.endDate(),
                request.enrollmentDeadline(), request.cancellationDeadlineDays());
        return ApiResponse.of(KlassResponse.from(klass));
    }

    @GetMapping
    public ApiResponse<List<KlassResponse>> list(
            @RequestHeader(ApiHeaders.USER_ID) Long userId,
            @RequestParam(required = false) KlassStatus status) {
        return ApiResponse.of(klassService.findAll(userId, status).stream()
                .map(KlassResponse::from)
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<KlassResponse> detail(@PathVariable Long id) {
        return ApiResponse.of(KlassResponse.from(klassService.findById(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<KlassResponse> patch(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id,
            @RequestBody KlassPatchRequest request) {
        Klass klass = klassService.update(
                id, creatorId,
                request.title(), request.description(), request.price(),
                request.maxCapacity(), request.startDate(), request.endDate(),
                request.enrollmentDeadline(), request.cancellationDeadlineDays());
        return ApiResponse.of(KlassResponse.from(klass));
    }

    @PostMapping("/{id}/open")
    public ApiResponse<KlassResponse> open(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id) {
        return ApiResponse.of(KlassResponse.from(klassService.open(id, creatorId)));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<KlassResponse> close(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id) {
        return ApiResponse.of(KlassResponse.from(klassService.close(id, creatorId)));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<KlassResponse> reopen(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id) {
        return ApiResponse.of(KlassResponse.from(klassService.reopen(id, creatorId)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id) {
        klassService.delete(id, creatorId);
        return ApiResponse.of(null);
    }

    @GetMapping("/{id}/students")
    public ApiResponse<List<StudentResponse>> students(
            @RequestHeader(ApiHeaders.USER_ID) Long creatorId,
            @PathVariable Long id) {
        return ApiResponse.of(klassService.listStudents(id, creatorId).stream()
                .map(StudentResponse::from)
                .toList());
    }
}
