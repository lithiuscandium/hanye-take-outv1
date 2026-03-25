package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端订单轨迹信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer orderId;
    /**
     * 派单状态：-1无派单 0已分配 1派送中 2已完成 3已取消
     */
    private Integer dispatchStatus;
    private String riderName;
    private String riderPhone;
    private Integer etaSec;
    private Integer progressIndex;
    private Integer totalPoints;
    private CampusPointVO shopPoint;
    private CampusPointVO dropoffPoint;
    private CampusPointVO riderPoint;
    @Builder.Default
    private List<CampusPointVO> routePoints = new ArrayList<>();
}

