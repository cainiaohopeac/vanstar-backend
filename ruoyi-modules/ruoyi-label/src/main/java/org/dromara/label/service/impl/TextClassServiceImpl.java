package org.dromara.label.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.TextNodeType;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextClassSet;
import org.dromara.label.mapper.TextClassMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.TextClassService;
import org.dromara.label.vo.TextClassVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文本标签体系管理。
 *
 * <p>标签用 {@code parentId} 表达层级，一次查询平铺取出后内存建树
 * （按层递归查询会随层级深度线性增加往返次数）。
 */
@Service
public class TextClassServiceImpl extends BaseService implements TextClassService {

  private final TextClassMapper textClassMapper;

  public TextClassServiceImpl(TextClassMapper textClassMapper) {
    this.textClassMapper = textClassMapper;
  }

  @Override
  public List<TextClassVO> tree(Long projectId) {
    if (projectId == null) {
      throw new ServiceException("projectId 不能为空");
    }
    return buildTree(textClassMapper.selectClasses(projectId));
  }

  @Override
  public List<LabelTextClassSet> classSets(Long projectId) {
    if (projectId == null) {
      throw new ServiceException("projectId 不能为空");
    }
    return textClassMapper.selectClassSets(projectId);
  }

  @Override
  @Transactional
  public Long saveClassSet(LabelTextClassSet row) {
    long userId = currentUserId();
    row.setUpdateBy(userId);
    if (row.getId() == null) {
      row.setCreateBy(userId);
      if (row.getStatus() == null) {
        row.setStatus("0");
      }
      textClassMapper.insertClassSet(row);
    } else {
      textClassMapper.updateClassSet(row);
    }
    return row.getId();
  }

  @Override
  @Transactional
  public Long saveClass(LabelTextClass row) {
    long userId = currentUserId();
    row.setUpdateBy(userId);
    validate(row);
    if (row.getId() == null) {
      row.setCreateBy(userId);
      if (row.getStatus() == null) {
        row.setStatus("0");
      }
      if (row.getSortOrder() == null) {
        row.setSortOrder(0);
      }
      textClassMapper.insertClass(row);
    } else {
      LabelTextClass existed = textClassMapper.selectClass(row.getId());
      if (existed == null) {
        throw new ServiceException("标签不存在：" + row.getId());
      }
      textClassMapper.updateClass(row);
    }
    return row.getId();
  }

  @Override
  @Transactional
  public void deleteClass(Long classId) {
    if (classId == null) {
      throw new ServiceException("classId 不能为空");
    }
    LabelTextClass existed = textClassMapper.selectClass(classId);
    if (existed == null) {
      throw new ServiceException("标签不存在：" + classId);
    }
    // 子标签还在时不能直接删，否则会留下一批父节点失效的孤儿标签
    boolean hasChildren = textClassMapper.selectClasses(existed.getProjectId()).stream()
        .anyMatch(c -> classId.equals(c.getParentId()));
    if (hasChildren) {
      throw new ServiceException("该标签下还有子标签，请先删除子标签");
    }
    long usage = textClassMapper.countEntityUsage(classId);
    if (usage > 0) {
      throw new ServiceException("该标签已被 " + usage + " 处标注使用，不可删除");
    }
    textClassMapper.softDeleteClass(classId, currentUserId());
  }

  /** 校验节点类型合法，且父标签确实存在（避免指向已删除或不存在的父节点）。 */
  private void validate(LabelTextClass row) {
    boolean validType = TextNodeType.ENTITY.equals(row.getNodeType())
        || TextNodeType.RELATION.equals(row.getNodeType())
        || TextNodeType.CLASSIFICATION.equals(row.getNodeType());
    if (!validType) {
      throw new ServiceException("标签类型不合法：" + row.getNodeType());
    }
    if (row.getParentId() != null && textClassMapper.selectClass(row.getParentId()) == null) {
      throw new ServiceException("父标签不存在：" + row.getParentId());
    }
  }

  private List<TextClassVO> buildTree(List<LabelTextClass> classes) {
    Map<Long, TextClassVO> byId = new HashMap<>();
    List<TextClassVO> roots = new ArrayList<>();
    for (LabelTextClass klass : classes) {
      TextClassVO vo = new TextClassVO();
      vo.setId(klass.getId());
      vo.setParentId(klass.getParentId());
      vo.setClassSetId(klass.getClassSetId());
      vo.setNodeType(klass.getNodeType());
      vo.setLabelName(klass.getLabelName());
      vo.setColor(klass.getColor());
      vo.setSortOrder(klass.getSortOrder());
      vo.setChildren(new ArrayList<>());
      byId.put(klass.getId(), vo);
    }
    for (LabelTextClass klass : classes) {
      TextClassVO vo = byId.get(klass.getId());
      TextClassVO parent = klass.getParentId() == null ? null : byId.get(klass.getParentId());
      if (parent == null) {
        roots.add(vo);
      } else {
        parent.getChildren().add(vo);
      }
    }
    return roots;
  }
}
