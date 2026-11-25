package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextClassSet;

/**
 * 文本标签体系数据访问（label_text_class_set / label_text_class）。
 *
 * <p>与视觉任务的 label_class 完全独立：文本标签需要层级（parent_id）与节点类型
 * （实体/关系/情感），套用视觉标签表会把两套语义塞进一张表，后续统计与导出都会混乱。
 */
public interface TextClassMapper {

  // ---------------- 标签集 ----------------

  /** 查询项目下的全部标签集。 */
  List<LabelTextClassSet> selectClassSets(@Param("projectId") Long projectId);

  /** 新建标签集，自增 id 回填到入参。 */
  int insertClassSet(LabelTextClassSet row);

  /** 更新标签集（名称/描述/状态）。 */
  int updateClassSet(LabelTextClassSet row);

  // ---------------- 标签 ----------------

  /** 查询项目下的全部标签（平铺，含各层级，由调用方组装成树）。 */
  List<LabelTextClass> selectClasses(@Param("projectId") Long projectId);

  /** 查询单个标签。 */
  LabelTextClass selectClass(@Param("id") Long id);

  /** 新建标签，自增 id 回填到入参。 */
  int insertClass(LabelTextClass row);

  /** 更新标签（名称/颜色/排序/父节点/状态）。 */
  int updateClass(LabelTextClass row);

  /**
   * 统计标签被实体引用的次数（用于「已使用的标签不可直接删除」校验）。
   *
   * <p>只统计未软删的实体行：历史软删行不阻止删除。
   */
  long countEntityUsage(@Param("classId") Long classId);

  /** 软删标签。调用方须先用 {@link #countEntityUsage} 校验未被使用。 */
  int softDeleteClass(@Param("id") Long id, @Param("userId") Long userId);
}
