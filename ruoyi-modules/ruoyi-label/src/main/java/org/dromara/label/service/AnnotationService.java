package org.dromara.label.service;

import java.util.List;
import java.util.Map;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.AnnotationSaveDTO;
import org.dromara.label.dto.ObjectCommentDTO;
import org.dromara.label.vo.AnnotationSaveResultVO;
import org.dromara.label.vo.AnnotationViewVO;
import org.dromara.label.vo.AssetAnnotationVO;
import org.dromara.label.vo.JobVO;
import org.dromara.label.vo.ObjectCommentVO;

/**
 * 标注域服务：标注工作台（作业标注取数/保存标注/资产标注）、标注员视角作业列表。
 */
public interface AnnotationService {

  /** 标注工作台：返回作业信息、资产列表、类别体系与已保存的标注对象。 */
  AnnotationViewVO annotation(Long jobId);

  /** 保存标注（status 可为草稿或提交等流转状态）。 */
  AnnotationSaveResultVO saveAnnotation(Long jobId, AnnotationSaveDTO dto, String status);

  /** 查询单个资产的标注对象。 */
  AssetAnnotationVO assetAnnotation(Long assetId);

  /**
   * 帧级全局属性值：指定资产的最近一次存档里 annotation_json $.globalAttrs。
   * 图片标注页切帧时拉取该帧的值回填「全局属性」弹窗；无存档返回空 Map。
   */
  Map<String, Object> assetGlobalAttrs(Long assetId);

  /** 标注员待办/我的作业列表。 */
  PageResult<JobVO> myJobs(String status);

  /** 查询作业的标注对象级批注（assetId 为空时返回该作业全部）。 */
  List<ObjectCommentVO> objectComments(Long jobId, Long assetId);

  /** 新增标注对象级批注。 */
  Long addObjectComment(Long jobId, ObjectCommentDTO dto);

  /** 删除标注对象级批注（仅创建人可删）。 */
  void deleteObjectComment(Long commentId);

  /** 更新批注修订状态（open / corrected），用于标注环节「确认已修改」。 */
  void updateObjectCommentStatus(Long commentId, String status);

  /** 标记 / 取消单帧无效（frameFlag=invalid/normal）。 */
  void markFrameInvalid(Long assetId, boolean invalid, String reason);
}
