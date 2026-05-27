package com.aiapi.image.repository;

import com.aiapi.image.entity.ImageGenerationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImageGenerationRecordRepository extends JpaRepository<ImageGenerationRecord, Long>, JpaSpecificationExecutor<ImageGenerationRecord> {
}
