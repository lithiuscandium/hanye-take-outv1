package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 校园路网节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusGraphNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer nodeId;

    private String name;

    private BigDecimal lng;

    private BigDecimal lat;

    /**
     * SHOP / DROPOFF / ROAD
     */
    private String nodeType;
}
