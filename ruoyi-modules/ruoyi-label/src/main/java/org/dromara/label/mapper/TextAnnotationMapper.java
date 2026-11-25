package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelAnnotation;
import org.dromara.label.entity.LabelAsset;
import org.dromara.label.entity.LabelDataEntry;
import org.dromara.label.entity.LabelJob;
import org.dromara.label.entity.LabelTextEntity;
import org.dromara.label.entity.LabelTextSentiment;
import org.dromara.label.vo.JobEntryProgressVO;
import org.dromara.label.vo.TextAssetVO;
import org.dromara.label.vo.TextEntityVO;
import org.dromara.label.vo.TextSentimentVO;

/**
 * 文本标注域数据访问（label_annotation / label_annotation_object 之外的文本专属表）。
 *
 * <p>入参一律用实体而非 {@code Map<String, Object>}：Map 的 key 写错只有运行到那一行才暴露，
 * 编译期完全看不出来。
 *
 * <p>写入全部走批量（多行 VALUES），调用方按 500 行一批切分。
 * label_annotation 与视觉标注共用一张表，这里只操作文本链路需要的列。
 */
public interface TextAnnotationMapper {

  // ---------------- 标注存档 ----------------

  /**
   * 查询作业下某资产的存档。文本条目是「一作业一资产」，最多一行。
   */
  LabelAnnotation selectAnnotation(@Param("jobId") Long jobId, @Param("assetId") Long assetId);

  /** 新建存档，自增 id 回填到入参对象的 {@code id}。 */
  int insertAnnotation(LabelAnnotation row);

  /** 更新存档状态与存档 JSON。 */
  int updateAnnotation(LabelAnnotation row);

  // ---------------- 资产（正文与句边界） ----------------

  /** 读取资产元信息（含 metaJson 句边界）。 */
  LabelAsset selectAsset(@Param("assetId") Long assetId);

  /** 导入解析完成后回写句边界与解析状态。 */
  int updateAssetMeta(LabelAsset row);

  // ---------------- 实体标注 ----------------

  /**
   * 查询某存档下的全部有效实体，联表带出标签名称与颜色（返回 VO）。
   */
  List<TextEntityVO> selectEntities(@Param("annotationId") Long annotationId);

  /**
   * 批量写入实体（一条 INSERT 带多行 VALUES）。
   *
   * <p>不支持自增 id 回写（批量 INSERT 只回填最后一行），调用方也不需要：
   * 实体之间以及后续的关系标注都用 {@code entityKey} 互相关联。
   */
  int insertEntityRows(@Param("list") List<LabelTextEntity> rows);

  /**
   * 软删某存档下的全部实体。
   *
   * <p>保存是全量语义（先删后插），所以这里不需要按区间精确匹配。
   * 带上 deleted = 0 条件避免重复更新历史软删行。
   */
  int softDeleteEntities(@Param("annotationId") Long annotationId, @Param("userId") Long userId);

  // ---------------- 情感分类 ----------------

  /** 查询某存档下的全部有效情感标注（条目级与句级），联表带出标签名称与颜色。 */
  List<TextSentimentVO> selectSentiments(@Param("annotationId") Long annotationId);

  /** 批量写入情感标注。 */
  int insertSentimentRows(@Param("list") List<LabelTextSentiment> rows);

  /** 软删某存档下的全部情感标注。 */
  int softDeleteSentiments(@Param("annotationId") Long annotationId, @Param("userId") Long userId);

  // ---------------- 状态推进（提交审核） ----------------

  /** 查询资产所属条目ID（无归属时为 null）。 */
  Long selectEntryIdByAssetId(@Param("assetId") Long assetId);

  /** 查询条目的当前标注状态（用于区分首次提交与修订提交）。 */
  String selectEntryLabelStatus(@Param("entryId") Long entryId);

  /** 提交后置资产为已标注/已修订，并重置审核状态为未审核。 */
  int updateAssetSubmitStatus(LabelAsset row);

  /** 更新条目标注状态。 */
  int updateEntryLabelStatus(LabelDataEntry row);

  /** 统计作业内条目的完成情况。 */
  JobEntryProgressVO selectJobEntryProgress(@Param("jobId") Long jobId);

  /** 查询作业内的文本条目（含条目标注状态，供标注页条目列表使用）。 */
  List<TextAssetVO> selectJobTextAssets(@Param("jobId") Long jobId);

  /** 推进作业状态与进度。 */
  int updateJobProgress(LabelJob row);
}
