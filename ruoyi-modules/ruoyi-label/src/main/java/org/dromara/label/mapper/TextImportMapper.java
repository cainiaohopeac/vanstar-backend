package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelAsset;
import org.dromara.label.entity.LabelDataEntry;
import org.dromara.label.entity.LabelUpload;

/**
 * 文本导入的数据访问（建条目与资产、回写关联）。
 *
 * <p>与视觉导入分开：视觉导入要处理 zip 解压、nuScenes 拓扑、按帧数拆条目，
 * 文本导入只需要「解析 → 分句 → 一篇一个条目」，逻辑完全不同，硬塞进同一处只会互相牵连。
 */
public interface TextImportMapper {

  /** 新建上传记录，自增 id 回填到入参（label_data_entry.upload_id 非空，必须先于条目创建）。 */
  int insertUpload(LabelUpload row);

  /** 新建条目，自增 id 回填到入参。 */
  int insertEntry(LabelDataEntry row);

  /** 新建资产，自增 id 回填到入参。 */
  int insertAsset(LabelAsset row);

  /** 资产建好后回填所属条目与首个资产指针。 */
  int bindAssetEntry(@Param("assetId") Long assetId, @Param("entryId") Long entryId,
                     @Param("updateBy") Long updateBy);

  /** 更新条目的资产数量与首个资产指针。 */
  int updateEntryAssetInfo(@Param("entryId") Long entryId, @Param("assetCount") Integer assetCount,
                           @Param("firstAssetId") Long firstAssetId, @Param("updateBy") Long updateBy);
}
