package org.dromara.label.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 文本标注工作台的一次性返回结构：打开标注页所需的全部数据。
 *
 * <p>一次请求给全（正文 + 句边界 + 实体 + 情感 + 标签树），
 * 避免前端打开页面后再发若干请求拼装——首屏往返次数直接影响进入标注的体感。
 *
 * <p>{@code text} 是整篇正文：前端按 {@code sentences} 分页渲染，
 * 而实体偏移是全文级的，切页不需要重新计算偏移。
 */
@Data
public class TextAnnotationVO implements Serializable {

  /** 作业ID */
  private Long jobId;
  /** 数据资产ID */
  private Long assetId;
  /** 项目ID（标注页按项目维度维护标签时需要） */
  private Long projectId;
  /** 标注存档ID（无存档时为 null，首次保存时创建） */
  private Long annotationId;
  /** 标注状态（draft / submitted） */
  private String status;
  /** 条目名称 */
  private String entryName;
  /** 文件名 */
  private String fileName;

  /** 正文全文 */
  private String text;
  /** 句边界（全文级偏移） */
  private List<TextSentenceVO> sentences;
  /** 字数 */
  private Integer charCount;

  /** 实体标注列表 */
  private List<TextEntityVO> entities;
  /** 情感分类列表（条目级与句级） */
  private List<TextSentimentVO> sentiments;

  /** 标签树（含实体、关系、情感三类节点） */
  private List<TextClassVO> classes;

  /** 整篇批注 */
  private String comment;
}
