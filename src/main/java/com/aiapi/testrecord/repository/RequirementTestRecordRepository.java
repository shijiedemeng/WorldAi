package com.aiapi.testrecord.repository;

import com.aiapi.testrecord.entity.RequirementTestRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementTestRecordRepository extends JpaRepository<RequirementTestRecord, Long> {

    long countByRequirementNo(String requirementNo);

    void deleteByRequirementNo(String requirementNo);

    long countByTesterAgentCode(String testerAgentCode);

    List<RequirementTestRecord> findByRequirementNo(String requirementNo);
}
