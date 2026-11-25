package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.PreAnnotationAssetVO;
import org.dromara.label.vo.PreAnnotationObjectVO;
import org.dromara.label.vo.PreAnnotationVO;
import org.dromara.label.vo.WorkClassVO;

import java.util.List;
import java.util.Map;

/**
 * 预标注域数据访问（label_pre_annotation / label_pre_annotation_object / label_asset / label_class）
 */
public interface PreAnnotationMapper {

    // ---------------- 取数 ----------------

    /** 上传数据下的数据资产（预标注按文件名匹配 nuScenes 帧的候选集）。 */
    List<PreAnnotationAssetVO> selectAssetsByUpload(@Param("uploadId") Long uploadId);

    /** 单个数据资产（定位其所属项目/批次/上传数据）。 */
    PreAnnotationAssetVO selectAsset(@Param("assetId") Long assetId);

    /** 数据资产最近一次预标注记录（不含预标注对象）。 */
    PreAnnotationVO selectPreAnnotation(@Param("assetId") Long assetId);

    /** 数据资产的预标注对象（3D 框）。 */
    List<PreAnnotationObjectVO> selectPreAnnotationObjects(@Param("assetId") Long assetId);

    /** 项目标签类别（用于按 nuScenes 类别名映射 class_id）。 */
    List<WorkClassVO> selectProjectClasses(@Param("projectId") Long projectId);

    /**
     * 项目当前有效的标签集 id。自动补建标签要挂到它下面；
     * 未启动/待配置的项目可能还没有标签集，此时返回 null，由调用方兜底创建。
     */
    Long selectClassSetId(@Param("projectId") Long projectId);

    /** 标签集内现有标签的最大排序号，新增标签排在后面；还没有标签时返回 0。 */
    Integer selectMaxClassSortOrder(@Param("classSetId") Long classSetId);

    /**
     * 补齐标签缺失的标注工具类型（只填空值，已有值的不动）。
     * 历史自动补建的标签 tool_type 为 NULL，导致标注页拿不到默认工具。
     */
    void updateClassToolType(@Param("classId") Long classId,
                             @Param("toolType") String toolType,
                             @Param("userId") Long userId);

    // ---------------- 写入 ----------------

    /** 写入预标注记录。 */
    int insertPreAnnotation(Map<String, Object> p);

    /** 写入预标注对象。 */
    int insertPreAnnotationObject(Map<String, Object> p);

    /** 软删数据资产名下的预标注对象（重新预标注时覆盖）。 */
    int softDeletePreAnnotationObjects(@Param("assetId") Long assetId,
                                       @Param("userId") Long userId);

    /** 软删数据资产名下的预标注记录（重新预标注时覆盖）。 */
    int softDeletePreAnnotation(@Param("assetId") Long assetId,
                                @Param("userId") Long userId);
}
