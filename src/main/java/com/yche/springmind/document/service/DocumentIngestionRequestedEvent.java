package com.yche.springmind.document.service;

public record DocumentIngestionRequestedEvent(
        Long documentId,
        Long groupId
) {
}
