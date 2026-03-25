package fun.cyhgraph.mapper;

import fun.cyhgraph.entity.DispatchTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DispatchTaskMapper {

    @Insert("insert into dispatch_task(order_id, rider_id, shop_node_id, dropoff_node_id, status, assign_score, eta_sec, route_node_ids, progress_index, assign_time, update_time) " +
            "values(#{orderId}, #{riderId}, #{shopNodeId}, #{dropoffNodeId}, #{status}, #{assignScore}, #{etaSec}, #{routeNodeIds}, #{progressIndex}, #{assignTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DispatchTask dispatchTask);

    @Select("select * from dispatch_task where order_id = #{orderId} order by id desc limit 1")
    DispatchTask getByOrderId(Integer orderId);

    @Select("select count(1) from dispatch_task where rider_id = #{riderId} and status in (0, 1)")
    Integer countActiveByRiderId(Integer riderId);

    @Select("select * from dispatch_task where rider_id = #{riderId} and status in (0, 1) order by id desc limit 1")
    DispatchTask getActiveByRiderId(Integer riderId);

    @Select("select * from dispatch_task where status = #{status}")
    List<DispatchTask> listByStatus(Integer status);

    @Update("update dispatch_task set status = #{status}, update_time = now() where order_id = #{orderId}")
    void updateStatusByOrderId(@Param("orderId") Integer orderId, @Param("status") Integer status);

    @Update("update dispatch_task set progress_index = #{progressIndex} where id = #{id}")
    void updateProgress(DispatchTask dispatchTask);

    @Update("update dispatch_task set rider_id = #{riderId}, assign_score = #{assignScore}, eta_sec = #{etaSec}, route_node_ids = #{routeNodeIds}, progress_index = #{progressIndex}, update_time = now() where id = #{id}")
    void updateAssignInfo(DispatchTask dispatchTask);
}
