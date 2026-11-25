package org.dromara.label.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * nuScenes 点云资产与相机图关联（label_nuscenes_asset_map）实体：
 * 一个点云样本（bin/pcd）对应多相机通道，一通道一行；
 * 由元数据 sample_data 的同帧关系推导，供标注工作台挂相机参考图、导出时定位标定参数。
 */
@Data
public class LabelNuscenesAssetMap implements Serializable {

  /** 关联ID */
  private Long id;
  /** 项目ID */
  private Long projectId;
  /** 上传数据ID */
  private Long uploadId;
  /** 点云数据资产ID（bin/pcd） */
  private Long assetId;
  /** nuScenes 样本Token */
  private String sampleToken;
  /** 点云 sample_data Token（导出坐标逆变换用） */
  private String sampleDataToken;
  /** 同帧相机图片的访问路径（file_url） */
  private String cameraFileUrl;
  /** 同帧相机图片资产ID */
  private Long cameraAssetId;
  /** 相机通道（CAM_FRONT 等） */
  private String cameraChannel;
  /** 同帧相机图片文件名（查询时 join label_asset 填充） */
  private String cameraFileName;
  /** 相机内参 [fx, fy, cx, cy]（JSON，投影用） */
  private String cameraIntrinsic;
  /** 相机外参四元数 [w, x, y, z]（JSON，投影用） */
  private String cameraRotation;
  /** 相机外参平移 [x, y, z]（JSON，投影用） */
  private String cameraTranslation;
  /** 相机图片宽（像素） */
  private Integer cameraWidth;
  /** 相机图片高（像素） */
  private Integer cameraHeight;
  /** 点云传感器（LIDAR）外参四元数 [w, x, y, z]（JSON，投影用） */
  private String lidarRotation;
  /** 点云传感器（LIDAR）外参平移 [x, y, z]（JSON，投影用） */
  private String lidarTranslation;
  /** 该帧自车位姿四元数 [w, x, y, z]（JSON，nuScenes ego_pose.rotation） */
  private String egoRotation;
  /** 该帧自车位姿平移 [x, y, z]（JSON，nuScenes ego_pose.translation） */
  private String egoTranslation;
  /** 操作人ID（写 created_by / updated_by） */
  private Long userId;
}
