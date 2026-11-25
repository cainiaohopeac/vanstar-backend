package org.dromara.label.service;

import java.util.List;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextClassSet;
import org.dromara.label.vo.TextClassVO;

/**
 * 文本标签体系管理（独立一套，与视觉标签 label_class 不互通）。
 */
public interface TextClassService {

  /** 查询项目下的标签树（含实体、关系、情感三类节点）。 */
  List<TextClassVO> tree(Long projectId);

  /** 查询项目下的标签集列表。 */
  List<LabelTextClassSet> classSets(Long projectId);

  /** 新建或更新标签集，返回标签集ID。 */
  Long saveClassSet(LabelTextClassSet row);

  /** 新建或更新标签，返回标签ID。 */
  Long saveClass(LabelTextClass row);

  /**
   * 删除标签。已被实体引用的标签不允许删除——删掉会让历史标注结果失去标签含义。
   */
  void deleteClass(Long classId);
}
