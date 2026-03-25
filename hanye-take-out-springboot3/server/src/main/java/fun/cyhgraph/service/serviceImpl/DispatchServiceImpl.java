package fun.cyhgraph.service.serviceImpl;

import com.alibaba.fastjson.JSON;
import fun.cyhgraph.constant.MessageConstant;
import fun.cyhgraph.entity.CampusEdge;
import fun.cyhgraph.entity.CampusNode;
import fun.cyhgraph.entity.DispatchTask;
import fun.cyhgraph.entity.Order;
import fun.cyhgraph.entity.Rider;
import fun.cyhgraph.entity.AddressBook;
import fun.cyhgraph.exception.OrderBusinessException;
import fun.cyhgraph.mapper.AddressBookMapper;
import fun.cyhgraph.mapper.CampusEdgeMapper;
import fun.cyhgraph.mapper.CampusNodeMapper;
import fun.cyhgraph.mapper.DispatchTaskMapper;
import fun.cyhgraph.mapper.OrderMapper;
import fun.cyhgraph.mapper.RiderMapper;
import fun.cyhgraph.service.DispatchService;
import fun.cyhgraph.vo.CampusPointVO;
import fun.cyhgraph.vo.DispatchDetailVO;
import fun.cyhgraph.vo.DispatchRiderCandidateVO;
import fun.cyhgraph.vo.OrderTrackVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DispatchServiceImpl implements DispatchService {

    private static final String NODE_TYPE_SHOP = "SHOP";
    private static final String NODE_TYPE_DROPOFF = "DROPOFF";
    private static final int AUTO_COMPLETE_DELAY_SEC = 15;

    @Autowired
    private RiderMapper riderMapper;
    @Autowired
    private CampusNodeMapper campusNodeMapper;
    @Autowired
    private CampusEdgeMapper campusEdgeMapper;
    @Autowired
    private DispatchTaskMapper dispatchTaskMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;

    @Override
    public void autoAssignOrder(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }
        DispatchTask exists = dispatchTaskMapper.getByOrderId(order.getId());
        if (exists != null) {
            return;
        }

        List<CampusNode> activeNodes = campusNodeMapper.listActive();
        List<CampusEdge> activeEdges = campusEdgeMapper.listActive();
        if (CollectionUtils.isEmpty(activeNodes) || CollectionUtils.isEmpty(activeEdges)) {
            log.warn("dispatch skipped, campus graph missing, orderId={}", order.getId());
            return;
        }

        Map<Integer, List<GraphEdge>> graph = buildGraph(activeEdges);

        CampusNode shopNode = resolveShopNode(activeNodes);
        CampusNode dropoffNode = resolveDropoffNode(order, activeNodes);
        if (shopNode == null || dropoffNode == null) {
            log.warn("dispatch skipped, shop/drop node missing, orderId={}", order.getId());
            return;
        }

        List<Rider> riders = riderMapper.listOnline();
        if (CollectionUtils.isEmpty(riders)) {
            log.warn("dispatch skipped, no online riders, orderId={}", order.getId());
            return;
        }

        List<DispatchCandidate> candidates = buildDispatchCandidates(
                riders,
                graph,
                shopNode.getId(),
                dropoffNode.getId(),
                null
        );
        if (CollectionUtils.isEmpty(candidates)) {
            log.warn("dispatch skipped, no valid rider path, orderId={}", order.getId());
            return;
        }
        DispatchCandidate bestCandidate = candidates.get(0);
        Rider bestRider = bestCandidate.rider;

        DispatchTask task = DispatchTask.builder()
                .orderId(order.getId())
                .riderId(bestRider.getId())
                .shopNodeId(shopNode.getId())
                .dropoffNodeId(dropoffNode.getId())
                .status(DispatchTask.ASSIGNED)
                .assignScore(BigDecimal.valueOf(bestCandidate.score).setScale(2, RoundingMode.HALF_UP))
                .etaSec(bestCandidate.totalEtaSec)
                .routeNodeIds(JSON.toJSONString(bestCandidate.deliveryPath.pathNodeIds))
                .progressIndex(0)
                .assignTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        dispatchTaskMapper.insert(task);
        log.info("dispatch success, orderId={}, riderId={}, score={}", order.getId(), bestRider.getId(), bestCandidate.score);
    }

    @Override
    public void onOrderDeliveryStart(Integer orderId) {
        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            return;
        }
        dispatchTaskMapper.updateStatusByOrderId(orderId, DispatchTask.DELIVERING);
        task.setProgressIndex(0);
        dispatchTaskMapper.updateProgress(task);

        Rider rider = riderMapper.getById(task.getRiderId());
        if (rider != null) {
            rider.setCurrentNodeId(task.getShopNodeId());
            riderMapper.updateCurrentNode(rider);
        }
    }

    @Override
    public void onOrderCompleted(Integer orderId) {
        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            return;
        }
        dispatchTaskMapper.updateStatusByOrderId(orderId, DispatchTask.COMPLETED);
        Rider rider = riderMapper.getById(task.getRiderId());
        if (rider != null) {
            rider.setCurrentNodeId(task.getDropoffNodeId());
            riderMapper.updateCurrentNode(rider);
        }
    }

    @Override
    public void onOrderCanceled(Integer orderId) {
        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            return;
        }
        dispatchTaskMapper.updateStatusByOrderId(orderId, DispatchTask.CANCELED);
    }

    @Override
    public void autoCompleteReachedOrders() {
        List<DispatchTask> deliveringTasks = dispatchTaskMapper.listByStatus(DispatchTask.DELIVERING);
        if (CollectionUtils.isEmpty(deliveringTasks)) {
            return;
        }
        List<CampusEdge> activeEdges = campusEdgeMapper.listActive();
        for (DispatchTask task : deliveringTasks) {
            if (task == null || task.getOrderId() == null) {
                continue;
            }
            List<Integer> routeNodeIds = resolveRouteNodeIds(task);
            int progressIndex = resolveProgressIndexByElapsed(task, routeNodeIds, activeEdges);
            if (task.getProgressIndex() == null || progressIndex != task.getProgressIndex()) {
                task.setProgressIndex(progressIndex);
                dispatchTaskMapper.updateProgress(task);
            }
            syncRiderNodeByProgress(task, routeNodeIds, progressIndex);

            int finalIndex = Math.max(0, routeNodeIds.size() - 1);
            long elapsedSec = resolveElapsedSec(task);
            long routeTotalSec = resolveRouteTotalSec(routeNodeIds, activeEdges);
            if (progressIndex >= finalIndex && elapsedSec >= routeTotalSec + AUTO_COMPLETE_DELAY_SEC) {
                markOrderCompletedByDispatch(task, finalIndex);
            }
        }
    }

    @Override
    public OrderTrackVO getTrackByOrderId(Integer orderId) {
        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            return OrderTrackVO.builder()
                    .orderId(orderId)
                    .dispatchStatus(-1)
                    .routePoints(new ArrayList<>())
                    .build();
        }

        List<CampusNode> activeNodes = campusNodeMapper.listActive();
        Map<Integer, CampusNode> nodeMap = toNodeMap(activeNodes);

        List<Integer> routeNodeIds = JSON.parseArray(task.getRouteNodeIds(), Integer.class);
        if (routeNodeIds == null) {
            routeNodeIds = new ArrayList<>();
        }
        if (routeNodeIds.isEmpty()) {
            routeNodeIds.add(task.getShopNodeId());
            routeNodeIds.add(task.getDropoffNodeId());
        }

        List<CampusEdge> activeEdges = campusEdgeMapper.listActive();
        Integer progressIndex = task.getProgressIndex() == null ? 0 : task.getProgressIndex();
        if (task.getStatus() != null && task.getStatus().equals(DispatchTask.DELIVERING)) {
            int timeProgressIndex = resolveProgressIndexByElapsed(task, routeNodeIds, activeEdges);
            progressIndex = Math.max(progressIndex, timeProgressIndex);
            if (!progressIndex.equals(task.getProgressIndex())) {
                task.setProgressIndex(progressIndex);
                dispatchTaskMapper.updateProgress(task);
            }
        }

        Rider rider = riderMapper.getById(task.getRiderId());
        if (rider != null && !routeNodeIds.isEmpty()) {
            int safeIdx = Math.max(0, Math.min(progressIndex, routeNodeIds.size() - 1));
            rider.setCurrentNodeId(routeNodeIds.get(safeIdx));
            riderMapper.updateCurrentNode(rider);
        }

        List<CampusPointVO> routePoints = new ArrayList<>();
        for (Integer nodeId : routeNodeIds) {
            CampusNode node = nodeMap.get(nodeId);
            if (node != null) {
                routePoints.add(toPoint(node));
            }
        }

        CampusNode shopNode = nodeMap.get(task.getShopNodeId());
        CampusNode dropoffNode = nodeMap.get(task.getDropoffNodeId());
        CampusNode riderNode = null;
        if (!routeNodeIds.isEmpty()) {
            int safeIdx = Math.max(0, Math.min(progressIndex, routeNodeIds.size() - 1));
            riderNode = nodeMap.get(routeNodeIds.get(safeIdx));
        }
        if (riderNode == null && rider != null) {
            riderNode = nodeMap.get(rider.getCurrentNodeId());
        }

        return OrderTrackVO.builder()
                .orderId(orderId)
                .dispatchStatus(task.getStatus())
                .riderName(rider == null ? "" : rider.getName())
                .riderPhone(rider == null ? "" : rider.getPhone())
                .etaSec(task.getEtaSec())
                .progressIndex(progressIndex)
                .totalPoints(routePoints.size())
                .shopPoint(shopNode == null ? null : toPoint(shopNode))
                .dropoffPoint(dropoffNode == null ? null : toPoint(dropoffNode))
                .riderPoint(riderNode == null ? null : toPoint(riderNode))
                .routePoints(routePoints)
                .build();
    }

    private int resolveProgressIndexByElapsed(DispatchTask task, List<Integer> routeNodeIds, List<CampusEdge> activeEdges) {
        if (CollectionUtils.isEmpty(routeNodeIds) || routeNodeIds.size() == 1) {
            return 0;
        }
        long elapsedSec = resolveElapsedSec(task);
        long acc = 0;
        int idx = 0;
        for (int i = 0; i < routeNodeIds.size() - 1; i++) {
            Integer from = routeNodeIds.get(i);
            Integer to = routeNodeIds.get(i + 1);
            int edgeSec = resolveEdgeCostSec(from, to, activeEdges);
            if (elapsedSec >= acc + edgeSec) {
                idx = i + 1;
                acc += edgeSec;
            } else {
                break;
            }
        }
        return idx;
    }

    private long resolveElapsedSec(DispatchTask task) {
        LocalDateTime baseTime = task.getUpdateTime() == null ? task.getAssignTime() : task.getUpdateTime();
        if (baseTime == null) {
            return 0;
        }
        return Math.max(0, Duration.between(baseTime, LocalDateTime.now()).getSeconds());
    }

    private long resolveRouteTotalSec(List<Integer> routeNodeIds, List<CampusEdge> activeEdges) {
        if (CollectionUtils.isEmpty(routeNodeIds) || routeNodeIds.size() == 1) {
            return 0;
        }
        long total = 0;
        for (int i = 0; i < routeNodeIds.size() - 1; i++) {
            total += resolveEdgeCostSec(routeNodeIds.get(i), routeNodeIds.get(i + 1), activeEdges);
        }
        return total;
    }

    private void syncRiderNodeByProgress(DispatchTask task, List<Integer> routeNodeIds, Integer progressIndex) {
        Rider rider = riderMapper.getById(task.getRiderId());
        if (rider == null || CollectionUtils.isEmpty(routeNodeIds)) {
            return;
        }
        int safeIdx = Math.max(0, Math.min(progressIndex == null ? 0 : progressIndex, routeNodeIds.size() - 1));
        Integer targetNodeId = routeNodeIds.get(safeIdx);
        if (targetNodeId != null && !targetNodeId.equals(rider.getCurrentNodeId())) {
            rider.setCurrentNodeId(targetNodeId);
            riderMapper.updateCurrentNode(rider);
        }
    }

    private void markOrderCompletedByDispatch(DispatchTask task, int finalIndex) {
        if (task == null || task.getOrderId() == null) {
            return;
        }
        Order order = orderMapper.getById(task.getOrderId());
        if (order == null) {
            return;
        }
        if (order.getStatus() != null && order.getStatus().equals(Order.DELIVERY_IN_PROGRESS)) {
            Order completedOrder = new Order();
            completedOrder.setId(order.getId());
            completedOrder.setStatus(Order.COMPLETED);
            completedOrder.setDeliveryTime(LocalDateTime.now());
            orderMapper.update(completedOrder);
            log.info("order auto-completed, orderId={}, delaySec={}", order.getId(), AUTO_COMPLETE_DELAY_SEC);
        }
        if (task.getProgressIndex() == null || task.getProgressIndex() < finalIndex) {
            task.setProgressIndex(finalIndex);
            dispatchTaskMapper.updateProgress(task);
        }
        dispatchTaskMapper.updateStatusByOrderId(task.getOrderId(), DispatchTask.COMPLETED);

        Rider rider = riderMapper.getById(task.getRiderId());
        if (rider != null && task.getDropoffNodeId() != null && !task.getDropoffNodeId().equals(rider.getCurrentNodeId())) {
            rider.setCurrentNodeId(task.getDropoffNodeId());
            riderMapper.updateCurrentNode(rider);
        }
    }

    private int resolveEdgeCostSec(Integer fromNodeId, Integer toNodeId, List<CampusEdge> activeEdges) {
        if (fromNodeId == null || toNodeId == null || CollectionUtils.isEmpty(activeEdges)) {
            return 60;
        }
        for (CampusEdge edge : activeEdges) {
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

    @Override
    public DispatchDetailVO getDispatchDetailByOrderId(Integer orderId) {
        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            return DispatchDetailVO.builder()
                    .orderId(orderId)
                    .dispatchStatus(-1)
                    .routeText("暂无派单记录")
                    .build();
        }

        List<CampusNode> activeNodes = campusNodeMapper.listActive();
        Map<Integer, CampusNode> nodeMap = toNodeMap(activeNodes);
        List<Integer> routeNodeIds = resolveRouteNodeIds(task);
        List<CampusPointVO> routePoints = new ArrayList<>();
        for (Integer nodeId : routeNodeIds) {
            CampusNode node = nodeMap.get(nodeId);
            if (node != null) {
                routePoints.add(toPoint(node));
            }
        }

        String routeText = routePoints.stream()
                .map(point -> point.getName() == null ? "节点-" + point.getNodeId() : point.getName())
                .collect(Collectors.joining(" -> "));
        if (routeText.isEmpty()) {
            routeText = "-";
        }

        Rider rider = riderMapper.getById(task.getRiderId());
        List<DispatchRiderCandidateVO> riderCandidates = buildRiderCandidates(
                task.getShopNodeId(),
                task.getDropoffNodeId(),
                task.getRiderId(),
                nodeMap
        );

        return DispatchDetailVO.builder()
                .orderId(orderId)
                .dispatchStatus(task.getStatus())
                .riderId(rider == null ? null : rider.getId())
                .riderName(rider == null ? "" : rider.getName())
                .riderPhone(rider == null ? "" : rider.getPhone())
                .assignScore(task.getAssignScore())
                .etaSec(task.getEtaSec())
                .progressIndex(task.getProgressIndex())
                .totalPoints(routePoints.size())
                .routeText(routeText)
                .routePoints(routePoints)
                .riderCandidates(riderCandidates)
                .build();
    }

    @Override
    public void manualReassign(Integer orderId, Integer riderId) {
        if (orderId == null || riderId == null) {
            throw new OrderBusinessException("参数错误");
        }

        DispatchTask task = dispatchTaskMapper.getByOrderId(orderId);
        if (task == null) {
            throw new OrderBusinessException("订单未生成派单任务");
        }
        if (task.getStatus() != null && (task.getStatus().equals(DispatchTask.COMPLETED) || task.getStatus().equals(DispatchTask.CANCELED))) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Rider rider = riderMapper.getById(riderId);
        if (rider == null || rider.getStatus() == null || rider.getStatus() != 1) {
            throw new OrderBusinessException("骑手不在线，无法改派");
        }

        List<CampusEdge> activeEdges = campusEdgeMapper.listActive();
        if (CollectionUtils.isEmpty(activeEdges)) {
            throw new OrderBusinessException("路网数据缺失，无法改派");
        }
        Map<Integer, List<GraphEdge>> graph = buildGraph(activeEdges);
        DispatchCandidate candidate = evaluateRider(
                rider,
                graph,
                task.getShopNodeId(),
                task.getDropoffNodeId(),
                task.getRiderId()
        );
        if (candidate == null) {
            throw new OrderBusinessException("该骑手当前无可用路径");
        }

        task.setRiderId(rider.getId());
        task.setAssignScore(BigDecimal.valueOf(candidate.score).setScale(2, RoundingMode.HALF_UP));
        task.setEtaSec(candidate.totalEtaSec);
        task.setRouteNodeIds(JSON.toJSONString(candidate.deliveryPath.pathNodeIds));
        task.setProgressIndex(0);
        task.setUpdateTime(LocalDateTime.now());
        dispatchTaskMapper.updateAssignInfo(task);

        if (task.getStatus() != null && task.getStatus().equals(DispatchTask.DELIVERING)) {
            rider.setCurrentNodeId(task.getShopNodeId());
            riderMapper.updateCurrentNode(rider);
        }

        log.info("manual reassign success, orderId={}, riderId={}, score={}", orderId, riderId, candidate.score);
    }

    private List<Integer> resolveRouteNodeIds(DispatchTask task) {
        List<Integer> routeNodeIds = JSON.parseArray(task.getRouteNodeIds(), Integer.class);
        if (routeNodeIds == null) {
            routeNodeIds = new ArrayList<>();
        }
        if (routeNodeIds.isEmpty()) {
            routeNodeIds.add(task.getShopNodeId());
            routeNodeIds.add(task.getDropoffNodeId());
        }
        return routeNodeIds;
    }

    private List<DispatchRiderCandidateVO> buildRiderCandidates(
            Integer shopNodeId,
            Integer dropoffNodeId,
            Integer currentTaskRiderId,
            Map<Integer, CampusNode> nodeMap
    ) {
        List<CampusEdge> activeEdges = campusEdgeMapper.listActive();
        if (CollectionUtils.isEmpty(activeEdges)) {
            return new ArrayList<>();
        }
        Map<Integer, List<GraphEdge>> graph = buildGraph(activeEdges);
        List<Rider> riders = riderMapper.listOnline();
        if (CollectionUtils.isEmpty(riders)) {
            return new ArrayList<>();
        }

        List<DispatchCandidate> candidates = buildDispatchCandidates(
                riders,
                graph,
                shopNodeId,
                dropoffNodeId,
                currentTaskRiderId
        );

        List<DispatchRiderCandidateVO> riderCandidates = new ArrayList<>();
        for (DispatchCandidate candidate : candidates) {
            CampusNode node = nodeMap.get(candidate.rider.getCurrentNodeId());
            riderCandidates.add(DispatchRiderCandidateVO.builder()
                    .riderId(candidate.rider.getId())
                    .riderName(candidate.rider.getName())
                    .riderPhone(candidate.rider.getPhone())
                    .activeLoad(candidate.activeLoad)
                    .score(BigDecimal.valueOf(candidate.score).setScale(2, RoundingMode.HALF_UP))
                    .pickupEtaSec((int) candidate.pickupPath.totalCost)
                    .deliveryEtaSec((int) candidate.deliveryPath.totalCost)
                    .totalEtaSec(candidate.totalEtaSec)
                    .currentNodeId(candidate.rider.getCurrentNodeId())
                    .currentNodeName(node == null ? "" : node.getName())
                    .build());
        }
        return riderCandidates;
    }

    private List<DispatchCandidate> buildDispatchCandidates(
            List<Rider> riders,
            Map<Integer, List<GraphEdge>> graph,
            Integer shopNodeId,
            Integer dropoffNodeId,
            Integer currentTaskRiderId
    ) {
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (Rider rider : riders) {
            DispatchCandidate candidate = evaluateRider(rider, graph, shopNodeId, dropoffNodeId, currentTaskRiderId);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((DispatchCandidate a) -> a.score)
                .thenComparingInt(a -> a.totalEtaSec));
        return candidates;
    }

    private DispatchCandidate evaluateRider(
            Rider rider,
            Map<Integer, List<GraphEdge>> graph,
            Integer shopNodeId,
            Integer dropoffNodeId,
            Integer currentTaskRiderId
    ) {
        if (rider == null || rider.getId() == null) {
            return null;
        }
        Integer currentNodeId = rider.getCurrentNodeId() == null ? shopNodeId : rider.getCurrentNodeId();
        PathResult pickupPath = shortestPath(currentNodeId, shopNodeId, graph);
        PathResult deliveryPath = shortestPath(shopNodeId, dropoffNodeId, graph);
        if (pickupPath == null || deliveryPath == null) {
            return null;
        }

        Integer load = dispatchTaskMapper.countActiveByRiderId(rider.getId());
        int activeLoad = load == null ? 0 : load;
        // 当前订单已在该骑手负载中，手工改派评分时扣掉一次，避免重复惩罚
        if (currentTaskRiderId != null && currentTaskRiderId.equals(rider.getId()) && activeLoad > 0) {
            activeLoad -= 1;
        }

        // 简化评分：到店时间 + 派送时间 + 在途负载惩罚
        double score = pickupPath.totalCost + deliveryPath.totalCost + activeLoad * 300.0;
        return new DispatchCandidate(
                rider,
                pickupPath,
                deliveryPath,
                activeLoad,
                score,
                (int) (pickupPath.totalCost + deliveryPath.totalCost)
        );
    }

    private CampusNode resolveShopNode(List<CampusNode> nodes) {
        for (CampusNode node : nodes) {
            if (NODE_TYPE_SHOP.equalsIgnoreCase(node.getNodeType())) {
                return node;
            }
        }
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private CampusNode resolveDropoffNode(Order order, List<CampusNode> nodes) {
        if (order != null && order.getAddressBookId() != null) {
            AddressBook addressBook = addressBookMapper.getById(order.getAddressBookId());
            if (addressBook != null && addressBook.getCampusNodeId() != null) {
                for (CampusNode node : nodes) {
                    if (addressBook.getCampusNodeId().equals(node.getId())) {
                        return node;
                    }
                }
            }
        }
        List<CampusNode> dropoffNodes = new ArrayList<>();
        for (CampusNode node : nodes) {
            if (NODE_TYPE_DROPOFF.equalsIgnoreCase(node.getNodeType())) {
                dropoffNodes.add(node);
            }
        }
        if (dropoffNodes.isEmpty()) {
            for (CampusNode node : nodes) {
                if (!NODE_TYPE_SHOP.equalsIgnoreCase(node.getNodeType())) {
                    dropoffNodes.add(node);
                }
            }
        }
        if (dropoffNodes.isEmpty()) {
            return null;
        }
        int seed = order.getAddressBookId() == null ? order.getId() : order.getAddressBookId();
        int idx = Math.abs(seed) % dropoffNodes.size();
        return dropoffNodes.get(idx);
    }

    private Map<Integer, CampusNode> toNodeMap(List<CampusNode> nodes) {
        Map<Integer, CampusNode> nodeMap = new HashMap<>();
        if (nodes == null) {
            return nodeMap;
        }
        for (CampusNode node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        return nodeMap;
    }

    private CampusPointVO toPoint(CampusNode node) {
        return CampusPointVO.builder()
                .nodeId(node.getId())
                .name(node.getName())
                .lng(node.getLng())
                .lat(node.getLat())
                .build();
    }

    private Map<Integer, List<GraphEdge>> buildGraph(List<CampusEdge> edges) {
        Map<Integer, List<GraphEdge>> graph = new HashMap<>();
        for (CampusEdge edge : edges) {
            graph.computeIfAbsent(edge.getFromNodeId(), k -> new ArrayList<>())
                    .add(new GraphEdge(edge.getToNodeId(), edge.getCostTimeSec()));
            if (edge.getBidirectional() != null && edge.getBidirectional() == 1) {
                graph.computeIfAbsent(edge.getToNodeId(), k -> new ArrayList<>())
                        .add(new GraphEdge(edge.getFromNodeId(), edge.getCostTimeSec()));
            }
        }
        return graph;
    }

    private PathResult shortestPath(Integer start, Integer target, Map<Integer, List<GraphEdge>> graph) {
        if (start == null || target == null) {
            return null;
        }
        if (start.equals(target)) {
            List<Integer> selfPath = new ArrayList<>();
            selfPath.add(start);
            return new PathResult(0.0, selfPath);
        }
        if (CollectionUtils.isEmpty(graph.get(start)) || CollectionUtils.isEmpty(graph.get(target))) {
            return null;
        }

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> prev = new HashMap<>();
        PriorityQueue<NodeState> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a.cost));
        dist.put(start, 0.0);
        pq.offer(new NodeState(start, 0.0));

        while (!pq.isEmpty()) {
            NodeState cur = pq.poll();
            if (cur.cost > dist.getOrDefault(cur.nodeId, Double.MAX_VALUE)) {
                continue;
            }
            if (cur.nodeId.equals(target)) {
                break;
            }
            List<GraphEdge> nextEdges = graph.getOrDefault(cur.nodeId, Collections.emptyList());
            for (GraphEdge edge : nextEdges) {
                double nextCost = cur.cost + edge.cost;
                if (nextCost < dist.getOrDefault(edge.to, Double.MAX_VALUE)) {
                    dist.put(edge.to, nextCost);
                    prev.put(edge.to, cur.nodeId);
                    pq.offer(new NodeState(edge.to, nextCost));
                }
            }
        }

        if (!dist.containsKey(target)) {
            return null;
        }
        List<Integer> path = new ArrayList<>();
        Integer cur = target;
        while (cur != null) {
            path.add(cur);
            cur = prev.get(cur);
        }
        Collections.reverse(path);
        return new PathResult(dist.get(target), path);
    }

    private static class DispatchCandidate {
        private final Rider rider;
        private final PathResult pickupPath;
        private final PathResult deliveryPath;
        private final int activeLoad;
        private final double score;
        private final int totalEtaSec;

        private DispatchCandidate(
                Rider rider,
                PathResult pickupPath,
                PathResult deliveryPath,
                int activeLoad,
                double score,
                int totalEtaSec
        ) {
            this.rider = rider;
            this.pickupPath = pickupPath;
            this.deliveryPath = deliveryPath;
            this.activeLoad = activeLoad;
            this.score = score;
            this.totalEtaSec = totalEtaSec;
        }
    }

    private static class GraphEdge {
        private final Integer to;
        private final double cost;

        private GraphEdge(Integer to, double cost) {
            this.to = to;
            this.cost = cost;
        }
    }

    private static class NodeState {
        private final Integer nodeId;
        private final double cost;

        private NodeState(Integer nodeId, double cost) {
            this.nodeId = nodeId;
            this.cost = cost;
        }
    }

    private static class PathResult {
        private final double totalCost;
        private final List<Integer> pathNodeIds;

        private PathResult(double totalCost, List<Integer> pathNodeIds) {
            this.totalCost = totalCost;
            this.pathNodeIds = pathNodeIds;
        }
    }
}
