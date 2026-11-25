package org.dromara.label.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 标注工作台「有效工时」心跳上报请求。
 *
 * <p>前端策略建议：工作台处于可见且最近 5 分钟有交互时，每 60s 上报一次；
 * 页面隐藏、失焦、无交互超过 5 分钟则停止上报，避免挂机刷工时。
 * 服务端对单次秒数做 1~600 的收敛，超出按边界处理。</p>
 */
@Data
public class StatsHeartbeatDTO implements Serializable {

    private Long projectId;

    private Long taskId;

    /** 岗位/节点，缺省 label。 */
    private String nodeKey;

    /** 本次心跳代表的活跃秒数，缺省 60。 */
    private Integer seconds;
}
