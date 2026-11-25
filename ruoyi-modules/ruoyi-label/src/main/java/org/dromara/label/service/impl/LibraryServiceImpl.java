package org.dromara.label.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.dto.LibraryImportDTO;
import org.dromara.label.dto.LibraryLabelDTO;
import org.dromara.label.dto.LibraryProjectComposeDTO;
import org.dromara.label.dto.LibraryQueryDTO;
import org.dromara.label.dto.LibrarySetDTO;
import org.dromara.label.entity.LabelTextClass;
import org.dromara.label.entity.LabelTextClassSet;
import org.dromara.label.mapper.LibraryMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.mapper.TextClassMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.LibraryService;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.LibraryLabelVO;
import org.dromara.label.vo.LibrarySetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签库域服务实现。
 *
 * <p>引入复制的关键口径：
 * <ul>
 *   <li>视觉：新建项目标签集后用单条 INSERT ... SELECT 复制（{@code copyVisual*ToClassSet}），
 *       列值与 {@code ProjectServiceImpl.createClass} 落库口径一致（is_rigid 空落 0、模板原样带过）；</li>
 *   <li>文本：label_text_class.parent_id 引用同表 id，无法一条 SQL 带映射复制，
 *       先整批插为顶层并记录「库标签id → 项目标签id」，再回填 parent_id 保留层级；
 *       任选组合时父未勾选的子标签提升为顶层（不引入未勾选的孤儿父节点）。</li>
 * </ul></p>
 */
@Service
public class LibraryServiceImpl extends BaseService implements LibraryService {

  /** 视觉场景取值：与前端页签一一对应 */
  private static final String SCENE_IMAGE = "image";
  private static final String SCENE_POINT_CLOUD = "point_cloud";
  private static final String SCENE_TEXT = "text";

  @Autowired
  private LibraryMapper libraryMapper;

  @Autowired
  private ProjectMapper projectMapper;

  @Autowired
  private TextClassMapper textClassMapper;

  // ---------------- 标签集 ----------------

  @Override
  public List<LibrarySetVO> sets(LibraryQueryDTO dto) {
    String scene = dto == null ? null : normalizeScene(dto.getScene());
    List<LibrarySetVO> list = libraryMapper.selectSets(scene);
    return list == null ? List.of() : list;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public LibrarySetVO saveSet(LibrarySetDTO dto) {
    if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
      throw new ServiceException("请填写标签集名称");
    }
    if (dto.getId() == null) {
      String scene = normalizeScene(dto.getScene());
      Map<String, Object> p = new HashMap<>();
      p.put("scene", scene);
      p.put("name", dto.getName().trim());
      p.put("description", dto.getDescription());
      p.put("sortOrder", dto.getSortOrder() == null ? 0 : dto.getSortOrder());
      p.put("userId", currentUserId());
      libraryMapper.insertSet(p);
      return libraryMapper.selectSetById(asLong(p.get("id")));
    }
    libraryMapper.updateSet(dto.getId(), dto.getName().trim(), dto.getDescription(), dto.getSortOrder(), currentUserId());
    return libraryMapper.selectSetById(dto.getId());
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteSet(LibraryQueryDTO dto) {
    if (dto == null || dto.getId() == null) {
      throw new ServiceException("缺少标签集ID");
    }
    libraryMapper.deleteSet(dto.getId(), currentUserId());
  }

  // ---------------- 标签 ----------------

  @Override
  public List<LibraryLabelVO> labels(LibraryQueryDTO dto) {
    if (dto == null || dto.getSetId() == null) {
      throw new ServiceException("缺少标签集ID");
    }
    List<LibraryLabelVO> list = libraryMapper.selectLabels(dto.getSetId());
    return list == null ? List.of() : list;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public LibraryLabelVO saveLabel(LibraryLabelDTO dto) {
    if (dto == null || dto.getLabelName() == null || dto.getLabelName().isBlank()) {
      throw new ServiceException("请填写标签名称");
    }
    if (dto.getId() == null) {
      if (dto.getSetId() == null) {
        throw new ServiceException("缺少所属标签集");
      }
      LibrarySetVO set = libraryMapper.selectSetById(dto.getSetId());
      if (set == null) {
        throw new ServiceException("所属标签集不存在或已删除");
      }
      boolean textScene = SCENE_TEXT.equals(set.getScene());
      // 文本集只认节点类型、视觉集只认工具/3D 字段：按集的场景收口，避免两边字段混存
      Map<String, Object> p = new HashMap<>();
      p.put("setId", dto.getSetId());
      p.put("labelName", dto.getLabelName().trim());
      p.put("color", dto.getColor());
      p.put("toolType", textScene ? null : dto.getToolType());
      p.put("nodeType", textScene ? normalizeNodeType(dto.getNodeType()) : null);
      p.put("defaultSizeL", textScene ? null : dto.getDefaultSizeL());
      p.put("defaultSizeW", textScene ? null : dto.getDefaultSizeW());
      p.put("defaultSizeH", textScene ? null : dto.getDefaultSizeH());
      p.put("isRigid", textScene ? 0 : rigidFlag(dto.getIsRigid(), 0));
      p.put("minInsidePoints", textScene ? null : dto.getMinInsidePoints());
      p.put("keypointTemplateJson",
          (!textScene && dto.getKeypointTemplate() != null) ? toJson(dto.getKeypointTemplate()) : null);
      p.put("sortOrder", dto.getSortOrder() == null ? 0 : dto.getSortOrder());
      p.put("userId", currentUserId());
      libraryMapper.insertLabel(p);
      return libraryMapper.selectLabelsByIds(List.of(asLong(p.get("id")))).get(0);
    }
    // 更新不按场景收口（历史数据允许纠偏），仅模板为 null 时保留原值（COALESCE）
    libraryMapper.updateLabel(dto.getId(), dto.getLabelName().trim(), dto.getColor(), dto.getToolType(),
        normalizeNodeType(dto.getNodeType()),
        dto.getDefaultSizeL(), dto.getDefaultSizeW(), dto.getDefaultSizeH(),
        rigidFlag(dto.getIsRigid(), null), dto.getMinInsidePoints(),
        dto.getKeypointTemplate() == null ? null : toJson(dto.getKeypointTemplate()),
        dto.getSortOrder(), currentUserId());
    return libraryMapper.selectLabelsByIds(List.of(dto.getId())).get(0);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteLabel(LibraryQueryDTO dto) {
    if (dto == null || dto.getId() == null) {
      throw new ServiceException("缺少标签ID");
    }
    libraryMapper.deleteLabel(dto.getId(), currentUserId());
  }

  // ---------------- 项目引入（视觉：label_class_set / label_class） ----------------

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ClassSetVO importSetToProject(LibraryImportDTO dto) {
    if (dto == null || dto.getProjectId() == null || dto.getLibrarySetId() == null) {
      throw new ServiceException("缺少项目ID或标签库集ID");
    }
    LibrarySetVO set = libraryMapper.selectSetById(dto.getLibrarySetId());
    if (set == null) {
      throw new ServiceException("标签库集不存在或已删除");
    }
    if (SCENE_TEXT.equals(set.getScene())) {
      throw new ServiceException("文字场景的标签集请走文本标签引入入口");
    }
    Long classSetId = createProjectClassSet(dto.getProjectId(), dto.getName(), set.getName());
    libraryMapper.copyVisualSetToClassSet(set.getId(), classSetId, currentUserId());
    return projectMapper.selectClassSetById(classSetId);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public ClassSetVO composeToProject(LibraryProjectComposeDTO dto) {
    if (dto == null || dto.getProjectId() == null) {
      throw new ServiceException("缺少项目ID");
    }
    List<Long> ids = validIds(dto.getLibraryLabelIds());
    if (ids.isEmpty()) {
      throw new ServiceException("请至少勾选一个标签");
    }
    List<LibraryLabelVO> labels = libraryMapper.selectLabelsByIds(ids);
    if (labels.size() != ids.size()) {
      throw new ServiceException("部分勾选的标签已不存在，请刷新后重选");
    }
    for (LibraryLabelVO label : labels) {
      if (SCENE_TEXT.equals(label.getScene())) {
        throw new ServiceException("勾选中含文字场景标签「" + label.getLabelName() + "」，请改走文本标签引入入口");
      }
    }
    Long classSetId = createProjectClassSet(dto.getProjectId(), dto.getName(), null);
    libraryMapper.copyVisualLabelsToClassSet(ids, classSetId, currentUserId());
    return projectMapper.selectClassSetById(classSetId);
  }

  // ---------------- 项目引入（文本：label_text_class_set / label_text_class） ----------------

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Long importTextSetToProject(LibraryImportDTO dto) {
    if (dto == null || dto.getProjectId() == null || dto.getLibrarySetId() == null) {
      throw new ServiceException("缺少项目ID或标签库集ID");
    }
    LibrarySetVO set = libraryMapper.selectSetById(dto.getLibrarySetId());
    if (set == null) {
      throw new ServiceException("标签库集不存在或已删除");
    }
    if (!SCENE_TEXT.equals(set.getScene())) {
      throw new ServiceException("视觉场景的标签集请走视觉标签引入入口");
    }
    return copyTextLabels(dto.getProjectId(), dto.getName(), set.getName(), libraryMapper.selectLabels(set.getId()));
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public Long composeTextToProject(LibraryProjectComposeDTO dto) {
    if (dto == null || dto.getProjectId() == null) {
      throw new ServiceException("缺少项目ID");
    }
    List<Long> ids = validIds(dto.getLibraryLabelIds());
    if (ids.isEmpty()) {
      throw new ServiceException("请至少勾选一个标签");
    }
    List<LibraryLabelVO> labels = libraryMapper.selectLabelsByIds(ids);
    if (labels.size() != ids.size()) {
      throw new ServiceException("部分勾选的标签已不存在，请刷新后重选");
    }
    for (LibraryLabelVO label : labels) {
      if (!SCENE_TEXT.equals(label.getScene())) {
        throw new ServiceException("勾选中含视觉场景标签「" + label.getLabelName() + "」，请改走视觉标签引入入口");
      }
    }
    return copyTextLabels(dto.getProjectId(), dto.getName(), null, labels);
  }

  /**
   * 复制文本标签到项目：新建文本标签集 → 逐条插入（先全部作为顶层）→ 按
   * 「库标签id → 项目标签id」映射回填 parent_id 保留层级。
   */
  private Long copyTextLabels(Long projectId, String name, String fallbackName, List<LibraryLabelVO> labels) {
    if (labels.isEmpty()) {
      throw new ServiceException("该标签集内没有标签，无法引入");
    }
    Long setId = createProjectTextClassSet(projectId, name, fallbackName);
    Long userId = currentUserId();
    Map<Long, Long> idMap = new HashMap<>();
    for (LibraryLabelVO label : labels) {
      LabelTextClass row = new LabelTextClass();
      row.setProjectId(projectId);
      row.setClassSetId(setId);
      row.setParentId(null);
      row.setNodeType(normalizeNodeType(label.getNodeType()));
      row.setLabelName(label.getLabelName());
      row.setColor(label.getColor());
      row.setSortOrder(label.getSortOrder() == null ? 0 : label.getSortOrder());
      row.setStatus("0");
      row.setCreateBy(userId);
      row.setUpdateBy(userId);
      textClassMapper.insertClass(row);
      idMap.put(label.getId(), row.getId());
    }
    // 层级回填：勾选范围内存在父子关系的补 parent_id；父未勾选的子标签保持顶层（孤儿父不引入）
    for (LibraryLabelVO label : labels) {
      Long newId = idMap.get(label.getId());
      Long parentNewId = label.getParentId() == null ? null : idMap.get(label.getParentId());
      if (newId != null && parentNewId != null && !parentNewId.equals(newId)) {
        libraryMapper.updateTextClassParent(newId, parentNewId);
      }
    }
    return setId;
  }

  // ---------------- 私有小工具 ----------------

  /** 新建项目视觉标签集：名称缺省回落库集名。 */
  private Long createProjectClassSet(Long projectId, String name, String fallbackName) {
    String finalName = requireName(name, fallbackName);
    Map<String, Object> p = new HashMap<>();
    p.put("projectId", projectId);
    p.put("name", finalName);
    p.put("version", null);
    p.put("userId", currentUserId());
    projectMapper.insertClassSet(p);
    return asLong(p.get("id"));
  }

  /** 新建项目文本标签集：名称缺省回落库集名。 */
  private Long createProjectTextClassSet(Long projectId, String name, String fallbackName) {
    LabelTextClassSet row = new LabelTextClassSet();
    row.setProjectId(projectId);
    row.setSetName(requireName(name, fallbackName));
    row.setStatus("0");
    row.setCreateBy(currentUserId());
    row.setUpdateBy(currentUserId());
    textClassMapper.insertClassSet(row);
    return row.getId();
  }

  /** 引入集合名称：显式传入优先，否则回落库集名；两者皆空视为参数缺失。 */
  private String requireName(String name, String fallbackName) {
    String finalName = name != null && !name.isBlank() ? name.trim() : fallbackName;
    if (finalName == null || finalName.isBlank()) {
      throw new ServiceException("请填写标签集合名称");
    }
    return finalName;
  }

  /** scene 归一化与校验：仅接受 image / point_cloud / text。 */
  private String normalizeScene(String scene) {
    if (scene == null || scene.isBlank()) {
      throw new ServiceException("缺少场景（image/point_cloud/text）");
    }
    return switch (scene.trim()) {
      case SCENE_IMAGE -> SCENE_IMAGE;
      case SCENE_POINT_CLOUD -> SCENE_POINT_CLOUD;
      case SCENE_TEXT -> SCENE_TEXT;
      default -> throw new ServiceException("未知场景：" + scene);
    };
  }

  /** 文本节点类型归一化：仅接受 entity / relation / classification，缺省 entity。 */
  private String normalizeNodeType(String nodeType) {
    if (nodeType == null || nodeType.isBlank()) {
      return "entity";
    }
    return switch (nodeType.trim()) {
      case "entity" -> "entity";
      case "relation" -> "relation";
      case "classification" -> "classification";
      default -> throw new ServiceException("未知文本节点类型：" + nodeType);
    };
  }

  /** 刚体标识 Boolean -> 0/1：库列为 tinyint；null 落 fallback（更新传 null 走 COALESCE 保留原值）。 */
  private Integer rigidFlag(Boolean value, Integer fallback) {
    if (value == null) {
      return fallback;
    }
    return value ? 1 : 0;
  }

  /** 勾选 id 去重清洗：剔除 null/非数字项，保持原顺序。 */
  private List<Long> validIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    Set<Long> seen = new HashSet<>();
    List<Long> out = new ArrayList<>();
    for (Long id : ids) {
      if (id != null && seen.add(id)) {
        out.add(id);
      }
    }
    return out;
  }
}
