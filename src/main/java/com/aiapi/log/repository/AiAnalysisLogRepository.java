package com.aiapi.log.repository;

import com.aiapi.log.entity.AiAnalysisLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiAnalysisLogRepository extends JpaRepository<AiAnalysisLog, Long>, JpaSpecificationExecutor<AiAnalysisLog> {

    @Query("""
            select
                log.id as id,
                log.sourceType as sourceType,
                log.businessNo as businessNo,
                log.projectCode as projectCode,
                log.aiSettingKey as aiSettingKey,
                log.modelName as modelName,
                log.status as status,
                log.errorMessage as errorMessage,
                log.createdAt as createdAt,
                log.updatedAt as updatedAt
            from AiAnalysisLog log
            where (:sourceType is null or log.sourceType = :sourceType)
              and (:projectCode is null or log.projectCode = :projectCode)
              and (:businessNo is null or log.businessNo = :businessNo)
              and (:status is null or log.status = :status)
              and (
                    :keyword is null
                    or log.businessNo like concat('%', :keyword, '%')
                    or log.projectCode like concat('%', :keyword, '%')
                    or log.aiSettingKey like concat('%', :keyword, '%')
                    or log.modelName like concat('%', :keyword, '%')
                    or log.errorMessage like concat('%', :keyword, '%')
              )
            """)
    Page<AiAnalysisLogListProjection> findListPage(@Param("sourceType") String sourceType,
                                                   @Param("projectCode") String projectCode,
                                                   @Param("businessNo") String businessNo,
                                                   @Param("status") String status,
                                                   @Param("keyword") String keyword,
                                                   Pageable pageable);
}
