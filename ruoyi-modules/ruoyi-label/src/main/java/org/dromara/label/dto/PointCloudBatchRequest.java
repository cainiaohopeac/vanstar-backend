package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量取点云帧的请求体（POST /uploads/point-cloud-batch）。
 *
 * <p>点云序列一帧一个文件，逐帧请求时浏览器同域名只有 6 条并发、其余全在排队，
 * 切帧就被拖慢。批量接口一次下发多帧，请求数从「每帧一次」降到「每批一次」。
 *
 * <p>urls 的顺序有意义：服务端按该顺序拼帧字节并返回 {@code {"lens":[...]}} 清单，
 * 因此调用方把「当前正在看的帧」放在首位，它的数据就会最先到达、最先渲染。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PointCloudBatchRequest implements Serializable {

    /**
     * 待取帧的点云文件地址列表（与 /uploads/** 文件网关同源，形如 /uploads/xxx.pcd）。
     * 支持完整 URL，服务端会自行剥掉域名部分取对象名；空白项会被忽略。
     */
    private List<String> urls;
}
