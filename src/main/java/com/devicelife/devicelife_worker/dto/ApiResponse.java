package com.devicelife.devicelife_worker.dto;

public record ApiResponse<T>(
        String code,
        String message,
        T result,
        boolean success
) {}