package com.aiapi.defect.repository;

import com.aiapi.defect.entity.DefectComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectCommentRepository extends JpaRepository<DefectComment, Long> {

    List<DefectComment> findByDefectRecordIdOrderByCommentedAtAscIdAsc(Long defectRecordId);
}
