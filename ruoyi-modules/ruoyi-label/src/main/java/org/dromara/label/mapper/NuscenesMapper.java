package org.dromara.label.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelNuscenesAssetMap;
import org.dromara.label.entity.LabelNuscenesMeta;

/**
 * nuScenes 元数据域数据访问（label_nuscenes_meta / label_nuscenes_asset_map）。
 * <p>写入参数一律使用实体；查询参数为单个标量（@Param），不使用 Map。</p>
 */
public interface NuscenesMapper {

    // ---------------- 元数据归档 ----------------

    /** 写入元数据归档记录。 */
    int insertMeta(LabelNuscenesMeta meta);

    /** 更新元数据归档的解析状态与说明。 */
    int updateMetaStatus(LabelNuscenesMeta meta);

    /** 按上传数据ID查询元数据归档（一条上传数据一份）。 */
    LabelNuscenesMeta selectMetaByUploadId(@Param("uploadId") Long uploadId);

    // ---------------- 点云-相机关联 ----------------

    /** 批量写入点云资产与相机图关联。 */
    int insertAssetMaps(List<LabelNuscenesAssetMap> maps);

    /** 软删一条上传数据名下的全部关联（重新解析时覆盖）。 */
    int softDeleteAssetMapsByUploadId(@Param("uploadId") Long uploadId,
                                      @Param("userId") Long userId);

    /** 按上传数据ID查询全部关联（导出与工作台取数用）。 */
    List<LabelNuscenesAssetMap> selectMapsByUploadId(@Param("uploadId") Long uploadId);

    /** 按点云资产ID列表查询关联（工作台按资产挂相机参考图）。 */
    List<LabelNuscenesAssetMap> selectMapsByAssetIds(@Param("assetIds") List<Long> assetIds);
}
