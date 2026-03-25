package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 校园路网边
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusGraphEdgeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer edgeId;

    private Integer fromNodeId;

    private Integer toNodeId;

    private Integer distanceM;

    private Integer costTimeSec;

    /**
     * 是否高亮（当前路线）
     */
    private Integer highlight;
}
