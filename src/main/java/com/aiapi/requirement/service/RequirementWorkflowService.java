package com.aiapi.requirement.service;

import com.aiapi.common.enums.RequirementExecutionMode;
import com.aiapi.common.enums.RequirementStatus;
import com.aiapi.common.enums.RequirementType;
import com.aiapi.common.exception.BizException;
import com.aiapi.requirement.dto.RequirementResponse;
import com.aiapi.requirement.dto.RequirementWorkflowEdgeResponse;
import com.aiapi.requirement.dto.RequirementWorkflowResponse;
import com.aiapi.requirement.dto.SaveRequirementWorkflowRequest;
import com.aiapi.requirement.entity.RequirementInfo;
import com.aiapi.requirement.entity.RequirementWorkflowEdge;
import com.aiapi.requirement.repository.RequirementWorkflowEdgeRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequirementWorkflowService {

    private final RequirementService requirementService;
    private final RequirementWorkflowEdgeRepository workflowEdgeRepository;

    @Transactional
    public RequirementWorkflowResponse getWorkflow(String requirementNo) {
        RequirementInfo root = resolveRoot(requirementNo);
        List<RequirementResponse> nodes = requirementService.listChildren(root.getRequirementNo());
        List<RequirementWorkflowEdge> edges = workflowEdgeRepository.findByRootRequirementNoOrderByIdAsc(root.getRequirementNo());
        return buildResponse(root.getRequirementNo(), nodes, edges);
    }

    @Transactional
    public RequirementWorkflowResponse saveWorkflow(String requirementNo, SaveRequirementWorkflowRequest request) {
        RequirementInfo root = resolveRoot(requirementNo);
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW) {
            throw new BizException(400, "only WORKFLOW requirement can save workflow");
        }
        List<RequirementResponse> nodes = requirementService.listChildren(root.getRequirementNo());
        List<RequirementWorkflowEdge> edges = buildValidatedEdges(root.getRequirementNo(), nodes, request == null ? List.of() : request.getEdges());
        workflowEdgeRepository.deleteByRootRequirementNo(root.getRequirementNo());
        workflowEdgeRepository.flush();
        if (!edges.isEmpty()) {
            workflowEdgeRepository.saveAll(edges);
        }
        return buildResponse(root.getRequirementNo(), nodes, edges);
    }

    @Transactional
    public void saveDefaultEdgesFromSortOrder(String rootRequirementNo, List<RequirementResponse> nodes) {
        RequirementInfo root = resolveRoot(rootRequirementNo);
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW || nodes == null || nodes.isEmpty()) {
            return;
        }
        List<SaveRequirementWorkflowRequest.Edge> requestEdges = new ArrayList<>();
        Map<Integer, List<RequirementResponse>> grouped = new LinkedHashMap<>();
        nodes.stream()
                .sorted(Comparator.comparing((RequirementResponse item) -> normalizeSortNo(item.getSortNo()))
                        .thenComparing(RequirementResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(item -> grouped.computeIfAbsent(normalizeSortNo(item.getSortNo()), ignored -> new ArrayList<>()).add(item));
        List<List<RequirementResponse>> groups = new ArrayList<>(grouped.values());
        for (int i = 0; i < groups.size() - 1; i++) {
            for (RequirementResponse from : groups.get(i)) {
                for (RequirementResponse to : groups.get(i + 1)) {
                    SaveRequirementWorkflowRequest.Edge edge = new SaveRequirementWorkflowRequest.Edge();
                    edge.setFromRequirementNo(from.getRequirementNo());
                    edge.setToRequirementNo(to.getRequirementNo());
                    requestEdges.add(edge);
                }
            }
        }
        if (requestEdges.isEmpty() && nodes.size() > 1) {
            List<RequirementResponse> sortedNodes = nodes.stream()
                    .sorted(Comparator.comparing((RequirementResponse item) -> normalizeSortNo(item.getSortNo()))
                            .thenComparing(RequirementResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (int i = 0; i < sortedNodes.size() - 1; i++) {
                SaveRequirementWorkflowRequest.Edge edge = new SaveRequirementWorkflowRequest.Edge();
                edge.setFromRequirementNo(sortedNodes.get(i).getRequirementNo());
                edge.setToRequirementNo(sortedNodes.get(i + 1).getRequirementNo());
                requestEdges.add(edge);
            }
        }
        SaveRequirementWorkflowRequest request = new SaveRequirementWorkflowRequest();
        request.setEdges(requestEdges);
        saveWorkflow(root.getRequirementNo(), request);
    }

    @Transactional
    public List<RequirementResponse> listReadyRequirements(String requirementNo) {
        RequirementInfo root = resolveRoot(requirementNo);
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW) {
            return List.of();
        }
        List<RequirementResponse> nodes = requirementService.listChildren(root.getRequirementNo());
        List<RequirementWorkflowEdge> edges = workflowEdgeRepository.findByRootRequirementNoOrderByIdAsc(root.getRequirementNo());
        return readyRequirements(nodes, edges);
    }

    @Transactional
    public List<RequirementResponse> listNextReadyRequirements(String completedRequirementNo) {
        RequirementInfo completed = requirementService.findEntity(completedRequirementNo);
        if (completed.getRequirementType() != RequirementType.SUB) {
            return List.of();
        }
        RequirementInfo root = resolveRoot(completed.getRootRequirementNo());
        if (root.getExecutionMode() != RequirementExecutionMode.WORKFLOW || !isFinished(completed.getStatus())) {
            return List.of();
        }
        return listReadyRequirements(root.getRequirementNo());
    }

    private RequirementWorkflowResponse buildResponse(String rootRequirementNo,
                                                      List<RequirementResponse> nodes,
                                                      List<RequirementWorkflowEdge> edges) {
        List<RequirementWorkflowEdgeResponse> edgeResponses = edges.stream()
                .map(this::toResponse)
                .toList();
        return RequirementWorkflowResponse.builder()
                .rootRequirementNo(rootRequirementNo)
                .nodes(nodes)
                .edges(edgeResponses)
                .startRequirementNos(startRequirementNos(nodes, edges))
                .readyRequirementNos(readyRequirements(nodes, edges).stream()
                        .map(RequirementResponse::getRequirementNo)
                        .toList())
                .build();
    }

    private List<RequirementWorkflowEdge> buildValidatedEdges(String rootRequirementNo,
                                                              List<RequirementResponse> nodes,
                                                              List<SaveRequirementWorkflowRequest.Edge> requestEdges) {
        Set<String> validRequirementNos = nodes.stream()
                .map(RequirementResponse::getRequirementNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<RequirementWorkflowEdge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SaveRequirementWorkflowRequest.Edge requestEdge : requestEdges == null ? List.<SaveRequirementWorkflowRequest.Edge>of() : requestEdges) {
            String from = trimToNull(requestEdge == null ? null : requestEdge.getFromRequirementNo());
            String to = trimToNull(requestEdge == null ? null : requestEdge.getToRequirementNo());
            if (from == null || to == null) {
                throw new BizException(400, "workflow edge from/to cannot be empty");
            }
            if (Objects.equals(from, to)) {
                throw new BizException(400, "workflow edge cannot point to itself");
            }
            if (!validRequirementNos.contains(from) || !validRequirementNos.contains(to)) {
                throw new BizException(400, "workflow edge can only use child requirements under current master");
            }
            String key = from + "->" + to;
            if (!seen.add(key)) {
                continue;
            }
            RequirementWorkflowEdge edge = new RequirementWorkflowEdge();
            edge.setRootRequirementNo(rootRequirementNo);
            edge.setFromRequirementNo(from);
            edge.setToRequirementNo(to);
            edges.add(edge);
        }
        ensureAcyclic(validRequirementNos, edges);
        ensureNoDetachedNodes(validRequirementNos, edges);
        return edges;
    }

    private void ensureNoDetachedNodes(Set<String> nodes, List<RequirementWorkflowEdge> edges) {
        if (nodes.size() <= 1) {
            return;
        }
        if (edges.isEmpty()) {
            throw new BizException(400, "workflow has detached nodes: " + String.join("、", nodes));
        }
        Set<String> linked = new HashSet<>();
        for (RequirementWorkflowEdge edge : edges) {
            linked.add(edge.getFromRequirementNo());
            linked.add(edge.getToRequirementNo());
        }
        List<String> detached = nodes.stream()
                .filter(node -> !linked.contains(node))
                .toList();
        if (!detached.isEmpty()) {
            throw new BizException(400, "workflow has detached nodes: " + String.join("、", detached));
        }
    }

    private void ensureAcyclic(Set<String> nodes, List<RequirementWorkflowEdge> edges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> nextMap = new HashMap<>();
        nodes.forEach(node -> indegree.put(node, 0));
        for (RequirementWorkflowEdge edge : edges) {
            nextMap.computeIfAbsent(edge.getFromRequirementNo(), ignored -> new ArrayList<>()).add(edge.getToRequirementNo());
            indegree.put(edge.getToRequirementNo(), indegree.getOrDefault(edge.getToRequirementNo(), 0) + 1);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((node, degree) -> {
            if (degree == 0) {
                queue.add(node);
            }
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            visited++;
            for (String next : nextMap.getOrDefault(node, List.of())) {
                int degree = indegree.get(next) - 1;
                indegree.put(next, degree);
                if (degree == 0) {
                    queue.addLast(next);
                }
            }
        }
        if (visited != nodes.size()) {
            throw new BizException(400, "workflow cannot contain circular dependency");
        }
    }

    private List<RequirementResponse> readyRequirements(List<RequirementResponse> nodes, List<RequirementWorkflowEdge> edges) {
        Map<String, RequirementResponse> nodeMap = nodes.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getRequirementNo(), item), LinkedHashMap::putAll);
        Map<String, List<String>> previousMap = previousMap(edges);
        return nodes.stream()
                .filter(this::isExecutable)
                .filter(this::reviewAllowsDispatch)
                .filter(node -> previousMap.getOrDefault(node.getRequirementNo(), List.of()).stream()
                        .map(nodeMap::get)
                        .filter(Objects::nonNull)
                        .allMatch(item -> isFinished(item.getStatus())))
                .toList();
    }

    private List<String> startRequirementNos(List<RequirementResponse> nodes, List<RequirementWorkflowEdge> edges) {
        Set<String> hasIncoming = edges.stream()
                .map(RequirementWorkflowEdge::getToRequirementNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return nodes.stream()
                .map(RequirementResponse::getRequirementNo)
                .filter(requirementNo -> !hasIncoming.contains(requirementNo))
                .toList();
    }

    private Map<String, List<String>> previousMap(List<RequirementWorkflowEdge> edges) {
        Map<String, List<String>> result = new HashMap<>();
        for (RequirementWorkflowEdge edge : edges) {
            result.computeIfAbsent(edge.getToRequirementNo(), ignored -> new ArrayList<>()).add(edge.getFromRequirementNo());
        }
        return result;
    }

    private RequirementWorkflowEdgeResponse toResponse(RequirementWorkflowEdge edge) {
        return RequirementWorkflowEdgeResponse.builder()
                .id(edge.getId())
                .fromRequirementNo(edge.getFromRequirementNo())
                .toRequirementNo(edge.getToRequirementNo())
                .build();
    }

    private RequirementInfo resolveRoot(String requirementNo) {
        RequirementInfo current = requirementService.findEntity(requirementNo);
        if (current.getRequirementType() == RequirementType.MASTER) {
            return current;
        }
        return requirementService.findEntity(current.getRootRequirementNo());
    }

    private boolean isExecutable(RequirementResponse item) {
        return item.getStatus() == RequirementStatus.PENDING || item.getStatus() == RequirementStatus.BLOCKED;
    }

    private boolean reviewAllowsDispatch(RequirementResponse item) {
        return !Boolean.TRUE.equals(item.getReviewRequiredFlag()) || Boolean.TRUE.equals(item.getReviewApprovedFlag());
    }

    private boolean isFinished(RequirementStatus status) {
        return status == RequirementStatus.DONE || status == RequirementStatus.CLOSED;
    }

    private int normalizeSortNo(Integer value) {
        return value == null ? 0 : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
