package fun.cyhgraph.service;

import fun.cyhgraph.vo.CampusGraphVO;

public interface CampusGraphService {

    CampusGraphVO getGraph();

    CampusGraphVO getGraphByOrderId(Integer orderId);

    CampusGraphVO getGraphByRiderId(Integer riderId);
}
