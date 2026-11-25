package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 传感器标定 VO：点云资产挂 LIDAR 外参，参考图项挂相机内外参，
 * 供前端做「点云 &lt;-&gt; 相机图」投影联动。
 */
@Data
public class CalibVO implements Serializable {

  /** 相机内参 [fx, fy, cx, cy]（仅相机有，点云为空） */
  private List<Double> intrinsic;
  /** 外参四元数 [w, x, y, z]（传感器坐标系 -&gt; 自车坐标系） */
  private List<Double> rotation;
  /** 外参平移 [x, y, z]（传感器坐标系 -&gt; 自车坐标系） */
  private List<Double> translation;
  /** 图片宽（像素，仅相机有） */
  private Integer width;
  /** 图片高（像素，仅相机有） */
  private Integer height;
}
