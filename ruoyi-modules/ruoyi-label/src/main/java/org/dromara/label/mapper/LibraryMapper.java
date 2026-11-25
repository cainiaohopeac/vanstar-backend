package org.dromara.label.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.LibraryLabelVO;
import org.dromara.label.vo.LibrarySetVO;

/**
 * 标签库数据访问（label_library_set / label_library_label）。
 *
 * <p>标签库是平台级共享资产：集按 scene（image/point_cloud/text）分面维护，
 * 项目侧引入时把库标签<b>复制</b>进项目自己的标签体系（视觉 → label_class_set/label_class，
 * 文本 → label_text_class_set/label_text_class），复制后与库解耦。</p>
 */
public interface LibraryMapper {

    // ---------------- 标签集 ----------------

    /** 按场景查标签集（含集内标签数），scene 为空查全部。 */
    List<LibrarySetVO> selectSets(@Param("scene") String scene);

    /** 查单个标签集。 */
    LibrarySetVO selectSetById(@Param("id") Long id);

    /** 新建标签集，自增 id 回填到入参（key: id）。 */
    int insertSet(Map<String, Object> p);

    /** 更新标签集（名称/描述/排序）。 */
    int updateSet(@Param("id") Long id, @Param("name") String name,
                  @Param("description") String description, @Param("sortOrder") Integer sortOrder,
                  @Param("userId") Long userId);

    /** 软删标签集（连同集内标签一并软删）。 */
    int deleteSet(@Param("id") Long id, @Param("userId") Long userId);

    // ---------------- 标签 ----------------

    /** 查集内全部标签（带集名/场景）。 */
    List<LibraryLabelVO> selectLabels(@Param("setId") Long setId);

    /** 按 id 批量查标签（任选组合引入用，带集名/场景）。 */
    List<LibraryLabelVO> selectLabelsByIds(@Param("ids") List<Long> ids);

    /** 新建标签，自增 id 回填到入参（key: id）。 */
    int insertLabel(Map<String, Object> p);

    /** 更新标签（名称/颜色/工具/节点类型/3D尺寸/刚体/最少点数/关键点模板/排序）。 */
    int updateLabel(@Param("id") Long id, @Param("labelName") String labelName,
                    @Param("color") String color, @Param("toolType") String toolType,
                    @Param("nodeType") String nodeType,
                    @Param("defaultSizeL") Double defaultSizeL, @Param("defaultSizeW") Double defaultSizeW,
                    @Param("defaultSizeH") Double defaultSizeH, @Param("isRigid") Integer isRigid,
                    @Param("minInsidePoints") Integer minInsidePoints,
                    @Param("keypointTemplateJson") String keypointTemplateJson,
                    @Param("sortOrder") Integer sortOrder, @Param("userId") Long userId);

    /** 软删标签。 */
    int deleteLabel(@Param("id") Long id, @Param("userId") Long userId);

    // ---------------- 项目引入（复制语义） ----------------

    /**
     * 整集复制：把标签库一个集的全部标签（视觉字段）复制进项目的 label_class。
     * 单条 INSERT ... SELECT 完成，无逐行往返。
     */
    int copyVisualSetToClassSet(@Param("setId") Long setId, @Param("classSetId") Long classSetId,
                                @Param("userId") Long userId);

    /**
     * 任选复制：把指定库标签（视觉字段，可跨集）复制进项目的 label_class。
     */
    int copyVisualLabelsToClassSet(@Param("ids") List<Long> ids, @Param("classSetId") Long classSetId,
                                   @Param("userId") Long userId);

    /** 文本标签回填父级：按「库标签id → 项目标签id」映射补 parent_id（层级保留，见 LibraryServiceImpl）。 */
    int updateTextClassParent(@Param("id") Long id, @Param("parentId") Long parentId);
}
