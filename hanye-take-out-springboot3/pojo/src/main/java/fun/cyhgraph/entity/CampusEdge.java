package fun.cyhgraph.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 校园路网边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer fromNodeId;
    private Integer toNodeId;
    /**
     * 路段长度（米）
     */
    private Integer distanceM;
    /**
     * 路段耗时（秒）
     */
    private Integer costTimeSec;
    /**
     * 1-双向 0-单向
     */
    private Integer bidirectional;
    /**
     * 1-启用 0-禁用
     */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

