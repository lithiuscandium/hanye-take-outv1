package fun.cyhgraph.service.serviceImpl;

import com.alibaba.fastjson.JSON;
import fun.cyhgraph.entity.CampusEdge;
import fun.cyhgraph.entity.CampusNode;
import fun.cyhgraph.entity.DispatchTask;
import fun.cyhgraph.entity.Rider;
import fun.cyhgraph.mapper.CampusEdgeMapper;
import fun.cyhgraph.mapper.CampusNodeMapper;
import fun.cyhgraph.mapper.DispatchTaskMapper;
import fun.cyhgraph.mapper.RiderMapper;
import fun.cyhgraph.service.CampusGraphService;
import fun.cyhgraph.vo.CampusGraphEdgeVO;
import fun.cyhgraph.vo.CampusGraphNodeVO;
import fun.cyhgraph.vo.CampusGraphVO;
import fun.cyhgraph.vo.CampusRiderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CampusGraphServiceImpl implements CampusGraphService {

    @Autowired
    private CampusNodeMapper campusNodeMapper;
    @Autowired
    private CampusEdgeMapper campusEdgeMapper;
    @Autowired
    private RiderMapper riderMapper;
    @Autowired
    private DispatchTaskMapper dispatchTaskMapper;

    @Override
    public CampusGraphVO getGraph() {
        return buildGraphVO(null, null);
    }

    @Override
    public CampusGraphVO getGraphByOrderId(Integer orderId) {
        DispatchTask task = orderId == null ? null : dispatchTaskMapper.getByOrderId(orderId);
        return buildGraphVO(task, null);
    }

    @Override
    public CampusGraphVO getGraphByRiderId(Integer riderId) {
        DispatchTask task = riderId == null ? null : dispatchTaskMapper.getActiveByRiderId(riderId);
        return buildGraphVO(task, riderId);
    }

    private CampusGraphVO buildGraphVO(DispatchTask dispatchTask, Integer focusRiderId) {
        List<CampusNode> nodes = campusNodeMapper.listActive();
        List<CampusEdge> edges = campusEdgeMapper.listActive();
        Map<Integer, CampusNode> nodeMap = new LinkedHashMap<>();
        for (CampusNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }

        List<Integer> routeNodeIds = resolveRouteNodeIds(dispatchTask);
        Set<String> routeEdgeSet = buildRouteEdgeSet(routeNodeIds);

        List<CampusGraphNodeVO> nodeVOS = new ArrayList<>();
        for (CampusNode node : nodes) {
            nodeVOS.add(CampusGraphNodeVO.builder()
                    .nodeId(node.getId())
                    .name(node.getName())
                    .lng(node.getLng())
                    .lat(node.getLat())
                    .nodeType(node.getNodeType())
                    .build());
        }

        List<CampusGraphEdgeVO> edgeVOS = new ArrayList<>();
        for (CampusEdge edge : edges) {
            String key = edge.getFromNodeId() + "-" + edge.getToNodeId();
            int highlight = routeEdgeSet.contains(key) ? 1 : 0;
            edgeVOS.add(CampusGraphEdgeVO.builder()
                    .edgeId(edge.getId())
                    .fromNodeId(edge.getFromNodeId())
                    .toNodeId(edge.getToNodeId())
                    .distanceM(edge.getDistanceM())
                    .costTimeSec(edge.getCostTimeSec())
                    .highlight(highlight)
                    .build());
            if (edge.getBidirectional() != null && edge.getBidirectional() == 1) {
                String reverseKey = edge.getToNodeId() + "-" + edge.getFromNodeId();
                int reverseHighlight = routeEdgeSet.contains(reverseKey) ? 1 : 0;
                edgeVOS.add(CampusGraphEdgeVO.builder()
                        .edgeId(edge.getId())
                        .fromNodeId(edge.getToNodeId())
                        .toNodeId(edge.getFromNodeId())
                        .distanceM(edge.getDistanceM())
                        .costTimeSec(edge.getCostTimeSec())
                        .highlight(reverseHighlight)
                        .build());
            }
        }

        String routeText = buildRouteText(routeNodeIds, nodeMap);
        List<CampusRiderVO> riderVOS = buildRiderMarkers(dispatchTask, focusRiderId, routeNodeIds, edges);
        Long dispatchStartTimeMs = null;
        if (dispatchTask != null && dispatchTask.getStatus() != null && dispatchTask.getStatus().equals(DispatchTask.DELIVERING)) {
            LocalDateTime baseTime = dispatchTask.getUpdateTime() == null ? dispatchTask.getAssignTime() : dispatchTask.getUpdateTime();
            if (baseTime != null) {
                dispatchStartTimeMs = baseTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        }

        return CampusGraphVO.builder()
                .orderId(dispatchTask == null ? null : dispatchTask.getOrderId())
                .riderId(dispatchTask == null ? focusRiderId : dispatchTask.getRiderId())
                .dispatchStatus(dispatchTask == null ? -1 : dispatchTask.getStatus())
                .dispatchStartTimeMs(dispatchStartTimeMs)
                .serverTimeMs(System.currentTimeMillis())
                .routeText(routeText)
                .routeNodeIds(routeNodeIds)
                .nodes(nodeVOS)
                .edges(edgeVOS)
                .riders(riderVOS)
                .build();
    }

    private List<CampusRiderVO> buildRiderMarkers(DispatchTask dispatchTask, Integer focusRiderId, List<Integer> routeNodeIds, List<CampusEdge> edges) {
        List<Rider> onlineRiders = riderMapper.listOnline();
        Map<Integer, Rider> riderMap = new HashMap<>();
        for (Rider rider : onlineRiders) {
            riderMap.put(rider.getId(), rider);
        }
        if (focusRiderId != null && !riderMap.containsKey(focusRiderId)) {
            Rider rider = riderMapper.getById(focusRiderId);
            if (rider != null) {
                riderMap.put(rider.getId(), rider);
            }
        }

        List<CampusRiderVO> riderVOS = new ArrayList<>();
        for (Rider rider : riderMap.values()) {
            Integer activeLoad = dispatchTaskMapper.countActiveByRiderId(rider.getId());
            int highlight = 0;
            Integer currentNodeId = rider.getCurrentNodeId();
            if (dispatchTask != null && dispatchTask.getRiderId() != null && dispatchTask.getRiderId().equals(rider.getId())) {
                highlight = 1;
                if (dispatchTask.getStatus() != null && dispatchTask.getStatus().equals(DispatchTask.DELIVERING)) {
                    int idx = resolveProgressIndexByElapsed(dispatchTask, routeNodeIds, edges);
                    if (!CollectionUtils.isEmpty(routeNodeIds)) {
                        int safeIdx = Math.max(0, Math.min(idx, routeNodeIds.size() - 1));
                        currentNodeId = routeNodeIds.get(safeIdx);
                    }
                }
            }
            if (focusRiderId != null && focusRiderId.equals(rider.getId())) {
                highlight = 1;
            }
            riderVOS.add(CampusRiderVO.builder()
                    .riderId(rider.getId())
                    .riderName(rider.getName())
                    .riderPhone(rider.getPhone())
                    .currentNodeId(currentNodeId)
                    .activeLoad(activeLoad == null ? 0 : activeLoad)
                    .highlight(highlight)
                    .build());
        }

        return riderVOS.stream()
                .sorted((a, b) -> {
                    int h = Integer.compare(b.getHighlight(), a.getHighlight());
                    return h != 0 ? h : Integer.compare(a.getRiderId(), b.getRiderId());
                })
                .collect(Collectors.toList());
    }

    private int resolveProgressIndexByElapsed(DispatchTask task, List<Integer> routeNodeIds, List<CampusEdge> edges) {
        if (CollectionUtils.isEmpty(routeNodeIds) || routeNodeIds.size() == 1) {
            return 0;
        }
        LocalDateTime baseTime = task.getUpdateTime() == null ? task.getAssignTime() : task.getUpdateTime();
        if (baseTime == null) {
            return task.getProgressIndex() == null ? 0 : task.getProgressIndex();
        }
        long elapsedSec = Math.max(0, Duration.between(baseTime, LocalDateTime.now()).getSeconds());
        long acc = 0;
        int idx = 0;
        for (int i = 0; i < routeNodeIds.size() - 1; i++) {
            Integer from = routeNodeIds.get(i);
            Integer to = routeNodeIds.get(i + 1);
            int edgeSec = resolveEdgeCostSec(from, to, edges);
            if (elapsedSec >= acc + edgeSec) {
                idx = i + 1;
                acc += edgeSec;
            } else {
                break;
            }
        }
        return idx;
    }

    private int resolveEdgeCostSec(Integer fromNodeId, Integer toNodeId, List<CampusEdge> edges) {
        if (fromNodeId == null || toNodeId == null || CollectionUtils.isEmpty(edges)) {
            return 60;
        }
        for (CampusEdge edge : edges) {
            if (edge == null || edge.getCostTimeSec() == null) {
                continue;
            }
            if (fromNodeId.equals(edge.getFromNodeId()) && toNodeId.equals(edge.getToNodeId())) {
                return edge.getCostTimeSec();
            }
            if (edge.getBidirectional() != null
                    && edge.getBidirectional() == 1
                    && fromNodeId.equals(edge.getToNodeId())
                    && toNodeId.equals(edge.getFromNodeId())) {
                return edge.getCostTimeSec();
            }
        }
        return 60;
    }

    private List<Integer> resolveRouteNodeIds(DispatchTask dispatchTask) {
        if (dispatchTask == null) {
            return new ArrayList<>();
        }
        List<Integer> routeNodeIds = JSON.parseArray(dispatchTask.getRouteNodeIds(), Integer.class);
        if (routeNodeIds == null) {
            routeNodeIds = new ArrayList<>();
        }
        if (routeNodeIds.isEmpty()) {
            if (dispatchTask.getShopNodeId() != null) {
                routeNodeIds.add(dispatchTask.getShopNodeId());
            }
            if (dispatchTask.getDropoffNodeId() != null) {
                routeNodeIds.add(dispatchTask.getDropoffNodeId());
            }
        }
        return routeNodeIds;
    }

    private Set<String> buildRouteEdgeSet(List<Integer> routeNodeIds) {
        Set<String> edgeSet = new HashSet<>();
        if (CollectionUtils.isEmpty(routeNodeIds)) {
            return edgeSet;
        }
        for (int i = 0; i < routeNodeIds.size() - 1; i++) {
            Integer from = routeNodeIds.get(i);
            Integer to = routeNodeIds.get(i + 1);
            if (from == null || to == null) {
                continue;
            }
            edgeSet.add(from + "-" + to);
            edgeSet.add(to + "-" + from);
        }
        return edgeSet;
    }

    private String buildRouteText(List<Integer> routeNodeIds, Map<Integer, CampusNode> nodeMap) {
        if (CollectionUtils.isEmpty(routeNodeIds)) {
            return "暂无高亮路线";
        }
        List<String> names = new ArrayList<>();
        for (Integer nodeId : routeNodeIds) {
            CampusNode node = nodeMap.get(nodeId);
            if (node == null) {
                names.add("节点-" + nodeId);
            } else {
                names.add(node.getName());
            }
        }
        return String.join(" -> ", names);
    }
}
