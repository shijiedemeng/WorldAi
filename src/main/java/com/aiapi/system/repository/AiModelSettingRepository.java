package com.aiapi.system.repository;

import com.aiapi.system.entity.AiModelSetting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelSettingRepository extends JpaRepository<AiModelSetting, Long> {

    Optional<AiModelSetting> findBySettingKey(String settingKey);
}
