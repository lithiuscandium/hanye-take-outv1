package fun.cyhgraph.service;

import fun.cyhgraph.entity.Order;
import fun.cyhgraph.vo.DispatchDetailVO;
import fun.cyhgraph.vo.OrderTrackVO;

public interface DispatchService {

    void autoAssignOrder(Order order);

    void onOrderDeliveryStart(Integer orderId);

    void onOrderCompleted(Integer orderId);

    void onOrderCanceled(Integer orderId);

    OrderTrackVO getTrackByOrderId(Integer orderId);

    DispatchDetailVO getDispatchDetailByOrderId(Integer orderId);

    void manualReassign(Integer orderId, Integer riderId);

    void autoCompleteReachedOrders();
}
