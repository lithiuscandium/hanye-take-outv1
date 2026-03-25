package fun.cyhgraph.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 人工改派参数
 */
@Data
public class DispatchReassignDTO implements Serializable {

    private Integer orderId;

    private Integer riderId;
}
