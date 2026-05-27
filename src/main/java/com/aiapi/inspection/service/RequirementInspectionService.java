package com.aiapi.inspection.service;

import com.aiapi.agent.entity.AgentInfo;
import com.aiapi.agent.service.AgentService;
import com.aiapi.common.enums.LinkStatus;
import com.aiapi.common.enums.LinkType;
import com.aiapi.inspection.dto.RequirementInspectionResponse;
import com.aiapi.link.entity.RequirementDevLink;
import com.aiapi.link.service.RequirementDevLinkService;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.service.RequirementService;
import com.aiapi.testrecord.entity.RequirementTestRecord;
import com.aiapi.testrecord.service.RequirementTestRecordService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementInspectionService {

    private final RequirementService requirementService;
    private final RequirementDevLinkService requirementDevLinkService;
    private final RequirementTestRecordService requirementTestRecordService;
    private final AgentService agentService;

    @Transactional(readOnly = true)
    public RequirementInspectionResponse inspect(String requirementNo) {
        RequirementInfo requirement = requirementService.findEntity(requirementNo);
        List<RequirementDevLink> links = requirementDevLinkService.findEntitiesByRequirementNo(requirementNo);
        List<RequirementTestRecord> tests = requirementTestRecordService.findEntitiesByRequirementNo(requirementNo);

        Set<LinkType> existingTypes = new LinkedHashSet<>();
        for (RequirementDevLink link : links) {
            existingTypes.add(link.getLinkType());
        }

        Set<String> missingItems = new LinkedHashSet<>();
        List<RequirementInspectionResponse.LinkIssue> incomplete = new ArrayList<>();
        List<RequirementInspectionResponse.LinkIssue> blocked = new ArrayList<>();
        Set<RequirementInspectionResponse.NotifyAgent> suggestedAgents = new LinkedHashSet<>();

        Set<LinkType> requiredLinkTypes = buildRequiredLinkTypes(requirement);
        if (links.isEmpty()) {
            missingItems.add("DEVELOPMENT_LINK");
        }
        for (LinkType requiredType : requiredLinkTypes) {
            if (!existingTypes.contains(requiredType)) {
                missingItems.add(requiredType.name());
            }
        }
        if (tests.isEmpty()) {
            missingItems.add("TEST_RECORD");
        }

        for (RequirementDevLink link : links) {
            if (link.getStatus() == LinkStatus.TODO || link.getStatus() == LinkStatus.DOING) {
                incomplete.add(toIssue(link));
                suggestedAgents.add(RequirementInspectionResponse.NotifyAgent.builder()
                        .agentCode(link.getAgentCode())
                        .reason("link " + link.getLinkType().name() + " not finished")
                        .build());
            }
            if (link.getStatus() == LinkStatus.BLOCKED) {
                blocked.add(toIssue(link));
                suggestedAgents.add(RequirementInspectionResponse.NotifyAgent.builder()
                        .agentCode(link.getAgentCode())
                        .reason("link " + link.getLinkType().name() + " blocked")
                        .build());
            }
        }

        for (String missingItem : missingItems) {
            for (AgentInfo agent : agentService.findOnlineAgents()) {
                String supported = agent.getSupportedLinkTypes() == null ? "" : agent.getSupportedLinkTypes().toUpperCase(Locale.ROOT);
                if (supported.contains(missingItem)) {
                    suggestedAgents.add(RequirementInspectionResponse.NotifyAgent.builder()
                            .agentCode(agent.getAgentCode())
                            .reason("supports missing item " + missingItem)
                            .build());
                }
            }
        }

        return RequirementInspectionResponse.builder()
                .requirementNo(requirement.getRequirementNo())
                .title(requirement.getTitle())
                .requirementStatus(requirement.getStatus())
                .existingLinks(List.copyOf(existingTypes))
                .missingItems(List.copyOf(missingItems))
                .incompleteLinks(incomplete)
                .blockedLinks(blocked)
                .suggestedAgents(new ArrayList<>(suggestedAgents))
                .build();
    }

    private Set<LinkType> buildRequiredLinkTypes(RequirementInfo requirement) {
        Set<LinkType> required = new LinkedHashSet<>();
        required.add(LinkType.BACKEND);
        required.add(LinkType.API);
        required.add(LinkType.DOCS);

        String text = ((requirement.getTitle() == null ? "" : requirement.getTitle()) + " "
                + (requirement.getRequirementDesc() == null ? "" : requirement.getRequirementDesc())).toLowerCase(Locale.ROOT);
        if (containsAny(text, List.of("front", "页面", "vue", "react", "h5", "web"))) {
            required.add(LinkType.FRONTEND);
        }
        if (containsAny(text, List.of("db", "sql", "表", "字段", "migration", "schema", "数据库"))) {
            required.add(LinkType.DB);
        }
        return required;
    }

    private RequirementInspectionResponse.LinkIssue toIssue(RequirementDevLink link) {
        return RequirementInspectionResponse.LinkIssue.builder()
                .linkId(link.getId())
                .linkType(link.getLinkType())
                .agentCode(link.getAgentCode())
                .status(link.getStatus())
                .summary(link.getResultSummary())
                .build();
    }

    private boolean containsAny(String source, List<String> keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
