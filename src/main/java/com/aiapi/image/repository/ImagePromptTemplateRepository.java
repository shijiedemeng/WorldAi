package com.aiapi.image.repository;

import com.aiapi.image.entity.ImagePromptTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ImagePromptTemplateRepository extends JpaRepository<ImagePromptTemplate, Long>, JpaSpecificationExecutor<ImagePromptTemplate> {

    Optional<ImagePromptTemplate> findByTemplateCode(String templateCode);

    boolean existsByTemplateCode(String templateCode);

    List<ImagePromptTemplate> findByTemplateCodeIn(Collection<String> templateCodes);
}
