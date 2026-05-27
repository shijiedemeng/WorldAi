package com.aiapi.defect.repository;

import com.aiapi.defect.entity.DefectRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectRecordRepository extends JpaRepository<DefectRecord, Long> {

    List<DefectRecord> findByProjectCodeOrderByUpdatedAtDesc(String projectCode);

    List<DefectRecord> findBySyncCodeOrderByUpdatedAtDesc(String syncCode);

    Optional<DefectRecord> findBySyncCodeAndExternalDefectId(String syncCode, String externalDefectId);
}
