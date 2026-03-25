package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 地图坐标点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusPointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer nodeId;
    private String name;
    private BigDecimal lng;
    private BigDecimal lat;
}

