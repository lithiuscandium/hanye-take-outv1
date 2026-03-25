package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端派单详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer orderId;

    /**
     * 派单状态：-1无派单 0已分配 1派送中 2已完成 3已取消
     */
    private Integer dispatchStatus;

    private Integer riderId;

    private String riderName;

    private String riderPhone;

    private BigDecimal assignScore;

    private Integer etaSec;

    private Integer progressIndex;

    private Integer totalPoints;

    private String routeText;

    @Builder.Default
    private List<CampusPointVO> routePoints = new ArrayList<>();

    @Builder.Default
    private List<DispatchRiderCandidateVO> riderCandidates = new ArrayList<>();
}
