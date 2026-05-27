package com.aiapi.defect.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class YunxiaoProjectMemberResponse {
    String roleId;
    String roleName;
    String userAvatar;
    String userId;
    String userName;
}
