package com.aiapi.defect.repository;

import com.aiapi.defect.entity.DefectSyncAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectSyncAccountRepository extends JpaRepository<DefectSyncAccount, Long> {

    Optional<DefectSyncAccount> findByAccountCode(String accountCode);
}
