package com.aiapi.defect.integration;

import com.aiapi.defect.entity.DefectSyncAccount;
import com.aiapi.defect.entity.DefectSyncProject;
import com.aiapi.defect.dto.DefectRemoteQueryRequest;

public interface DefectPlatformClient {

    DefectPlatformBugPage fetchBugs(DefectSyncAccount account, DefectSyncProject syncProject, DefectRemoteQueryRequest query);

    DefectPlatformBug fetchBug(DefectSyncAccount account, DefectSyncProject syncProject, String externalDefectId);
}
