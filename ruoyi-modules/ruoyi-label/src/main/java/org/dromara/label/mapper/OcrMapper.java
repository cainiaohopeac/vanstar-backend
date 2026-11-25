package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.label.ocr.dto.AssetImageRef;

/**
 * OCR 相关只读数据访问。
 */
@Mapper
public interface OcrMapper {

    /**
     * 按资产ID取取图引用（fileUrl + mediaType）。
     */
    AssetImageRef selectAssetById(@Param("assetId") Long assetId);
}
