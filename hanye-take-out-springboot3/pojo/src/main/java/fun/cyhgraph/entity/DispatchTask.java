package fun.cyhgraph.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 派单任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchTask implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Integer ASSIGNED = 0;
    public static final Integer DELIVERING = 1;
    public static final Integer COMPLETED = 2;
    public static final Integer CANCELED = 3;

    private Integer id;
    private Integer orderId;
    private Integer riderId;
    private Integer shopNodeId;
    private Integer dropoffNodeId;
    /**
     * 0-已分配 1-派送中 2-已完成 3-已取消
     */
    private Integer status;
    private BigDecimal assignScore;
    /**
     * 预计送达秒数
     */
    private Integer etaSec;
    /**
     * 路线节点id列表（JSON）
     */
    private String routeNodeIds;
    /**
     * 当前进度索引，按 routeNodeIds 推进
     */
    private Integer progressIndex;
    private LocalDateTime assignTime;
    private LocalDateTime updateTime;
}

