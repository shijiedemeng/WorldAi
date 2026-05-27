package com.aiapi.defect.repository;

import com.aiapi.defect.entity.DefectAnalysisRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectAnalysisRecordRepository extends JpaRepository<DefectAnalysisRecord, Long> {

    Optional<DefectAnalysisRecord> findByAnalysisNo(String analysisNo);

    List<DefectAnalysisRecord> findByProjectCodeOrderByUpdatedAtDesc(String projectCode);

    List<DefectAnalysisRecord> findByDefectRecordIdOrderByUpdatedAtDesc(Long defectRecordId);

    List<DefectAnalysisRecord> findByNextRequirementNoIn(Collection<String> nextRequirementNos);

    void deleteByNextRequirementNoIn(Collection<String> nextRequirementNos);
}
