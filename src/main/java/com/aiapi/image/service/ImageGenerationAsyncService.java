package com.aiapi.image.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationAsyncService {

    private final ImageGenerationService imageGenerationService;

    @Async("aiApiTaskExecutor")
    public void submit(Long recordId) {
        try {
            imageGenerationService.processRecord(recordId);
        } catch (Exception ex) {
            log.error("image generation failed for record {}: {}", recordId, ex.getMessage(), ex);
        }
    }
}
