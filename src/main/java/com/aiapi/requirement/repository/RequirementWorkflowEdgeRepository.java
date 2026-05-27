package com.aiapi.requirement.repository;

import com.aiapi.requirement.entity.RequirementWorkflowEdge;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementWorkflowEdgeRepository extends JpaRepository<RequirementWorkflowEdge, Long> {

    List<RequirementWorkflowEdge> findByRootRequirementNoOrderByIdAsc(String rootRequirementNo);

    List<RequirementWorkflowEdge> findByFromRequirementNo(String fromRequirementNo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RequirementWorkflowEdge edge
            where edge.rootRequirementNo = :rootRequirementNo
            """)
    void deleteByRootRequirementNo(@Param("rootRequirementNo") String rootRequirementNo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RequirementWorkflowEdge edge
            where edge.rootRequirementNo = :rootRequirementNo
              and (edge.fromRequirementNo in :requirementNos or edge.toRequirementNo in :requirementNos)
            """)
    void deleteByRootRequirementNoAndRequirementNoIn(@Param("rootRequirementNo") String rootRequirementNo,
                                                    @Param("requirementNos") Collection<String> requirementNos);
}
