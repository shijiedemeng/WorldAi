package com.aiapi.defect.integration;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class YunxiaoProjectMember {
    String roleId;
    String roleName;
    String userAvatar;
    String userId;
    String userName;
}
