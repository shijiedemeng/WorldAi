package com.aiapi.markdown.repository;

import com.aiapi.markdown.entity.MarkdownDocument;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MarkdownDocumentRepository extends JpaRepository<MarkdownDocument, Long>, JpaSpecificationExecutor<MarkdownDocument> {

    Optional<MarkdownDocument> findByDocumentId(String documentId);

    boolean existsByDocumentId(String documentId);

    boolean existsByParentId(String parentId);

    List<MarkdownDocument> findByDocumentIdIn(Collection<String> documentIds);
}
