package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 改派候选骑手信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRiderCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer riderId;

    private String riderName;

    private String riderPhone;

    private Integer activeLoad;

    /**
     * 简化派单评分（越低越优）
     */
    private BigDecimal score;

    private Integer pickupEtaSec;

    private Integer deliveryEtaSec;

    private Integer totalEtaSec;

    private Integer currentNodeId;

    private String currentNodeName;
}
