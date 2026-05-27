package com.aiapi.knowledge.repository;

import com.aiapi.knowledge.entity.ProjectKnowledgeItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectKnowledgeItemRepository extends JpaRepository<ProjectKnowledgeItem, Long>, JpaSpecificationExecutor<ProjectKnowledgeItem> {

    List<ProjectKnowledgeItem> findByProjectCode(String projectCode);

    List<ProjectKnowledgeItem> findBySourceRequirementNo(String sourceRequirementNo);

    long countByProjectCode(String projectCode);
}
