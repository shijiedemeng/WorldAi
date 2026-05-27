package com.aiapi.inspection;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiapi.agent.service.AgentService;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.inspection.dto.RequirementInspectionResponse;
import com.aiapi.inspection.service.RequirementInspectionService;
import com.aiapi.link.entity.RequirementDevLink;
import com.aiapi.link.service.RequirementDevLinkService;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.testrecord.service.RequirementTestRecordService;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequirementInspectionServiceTest {

    @Mock
    private RequirementService requirementService;
    @Mock
    private RequirementDevLinkService requirementDevLinkService;
    @Mock
    private RequirementTestRecordService requirementTestRecordService;
    @Mock
    private AgentService agentService;

    @InjectMocks
    private RequirementInspectionService requirementInspectionService;

    @Test
    void shouldReportMissingItemsAndIncompleteLinks() {
        RequirementInfo requirement = new RequirementInfo();
        requirement.setRequirementNo("REQ-1");
        requirement.setTitle("backend api docs requirement");
        requirement.setStatus(RequirementStatus.IN_PROGRESS);

        RequirementDevLink link = new RequirementDevLink();
        link.setId(1L);
        link.setLinkType(LinkType.BACKEND);
        link.setAgentCode("agent-dev");
        link.setStatus(LinkStatus.DOING);

        when(requirementService.findEntity("REQ-1")).thenReturn(requirement);
        when(requirementDevLinkService.findEntitiesByRequirementNo("REQ-1")).thenReturn(List.of(link));
        when(requirementTestRecordService.findEntitiesByRequirementNo("REQ-1")).thenReturn(Collections.emptyList());
        when(agentService.findOnlineAgents()).thenReturn(Collections.emptyList());

        RequirementInspectionResponse response = requirementInspectionService.inspect("REQ-1");

        assertTrue(response.getMissingItems().contains("API"));
        assertTrue(response.getMissingItems().contains("DOCS"));
        assertTrue(response.getMissingItems().contains("TEST_RECORD"));
        assertTrue(response.getIncompleteLinks().stream().anyMatch(item -> item.getLinkId().equals(1L)));
    }
}
