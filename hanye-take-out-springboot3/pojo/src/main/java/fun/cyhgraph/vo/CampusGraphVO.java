package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 校园路网可视化数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusGraphVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer orderId;

    private Integer riderId;

    /**
     * -1无派单 0已分配 1派送中 2已完成 3已取消
     */
    private Integer dispatchStatus;

    /**
     * 派送起算时间（毫秒时间戳），用于前端连续动画
     */
    private Long dispatchStartTimeMs;

    /**
     * 服务端当前时间（毫秒时间戳），用于前端和服务端时间对齐
     */
    private Long serverTimeMs;

    private String routeText;

    @Builder.Default
    private List<Integer> routeNodeIds = new ArrayList<>();

    @Builder.Default
    private List<CampusGraphNodeVO> nodes = new ArrayList<>();

    @Builder.Default
    private List<CampusGraphEdgeVO> edges = new ArrayList<>();

    @Builder.Default
    private List<CampusRiderVO> riders = new ArrayList<>();
}
