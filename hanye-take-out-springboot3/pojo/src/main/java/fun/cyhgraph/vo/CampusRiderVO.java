package fun.cyhgraph.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 路网图中的骑手标记
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusRiderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer riderId;

    private String riderName;

    private String riderPhone;

    private Integer currentNodeId;

    private Integer activeLoad;

    /**
     * 1-当前派单骑手 0-普通骑手
     */
    private Integer highlight;
}
