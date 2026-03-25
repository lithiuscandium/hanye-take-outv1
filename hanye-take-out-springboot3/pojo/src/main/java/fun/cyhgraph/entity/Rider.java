package fun.cyhgraph.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 骑手
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rider implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String name;
    private String phone;
    /**
     * 1-在线 0-离线
     */
    private Integer status;
    /**
     * 最大并单数
     */
    private Integer maxLoad;
    /**
     * 平均速度(米/秒)
     */
    private BigDecimal speedMps;
    /**
     * 当前所在路网节点
     */
    private Integer currentNodeId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

