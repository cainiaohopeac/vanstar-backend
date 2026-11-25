package org.dromara.label.service;

import java.util.List;
import org.dromara.label.dto.TextAnnotationSaveDTO;
import org.dromara.label.vo.TextAnnotationSaveResultVO;
import org.dromara.label.vo.TextAnnotationVO;
import org.dromara.label.vo.TextAssetVO;

/**
 * 文本标注工作台：加载与保存。
 *
 * <p>与视觉标注（AnnotationService）完全分开：文本没有帧、插值、轨迹等概念，
 * 标注单位是「字符偏移 + 实体关系 + 情感分类」，混进视觉逻辑只会互相拖累。
 * 两者共用底层的作业/条目/资产/审核表，因此任务分配、审核流程、统计是统一的。
 */
public interface TextAnnotationService {

  /**
   * 加载标注工作台数据（正文、句边界、已有实体与情感、标签树）。
   *
   * @param jobId   作业ID
   * @param assetId 资产ID（一篇文本）
   */
  TextAnnotationVO view(Long jobId, Long assetId);

  /** 查询作业内的文本条目列表（标注页据此切换条目）。 */
  List<TextAssetVO> jobAssets(Long jobId);

  /**
   * 保存文本标注（草稿或提交审核）。
   *
   * <p>全量语义：入参的实体与情感即画布上的完整结果，服务端先软删该存档下的旧数据再整体写入。
   */
  TextAnnotationSaveResultVO save(TextAnnotationSaveDTO dto);
}
