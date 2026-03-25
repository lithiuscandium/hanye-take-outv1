package fun.cyhgraph.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 校园路网节点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private BigDecimal lng;
    private BigDecimal lat;
    /**
     * 节点类型：SHOP/DROPOFF/ROAD
     */
    private String nodeType;
    /**
     * 1-启用 0-禁用
     */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

