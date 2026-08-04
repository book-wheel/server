package com.bookwheel.server.common.dto;

public record S3ObjectMetadata(
        long contentLength,
        String contentType,
        String eTag
) {
}
