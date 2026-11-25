package org.dromara.label.service;

import org.dromara.label.dto.TextImportDTO;
import org.dromara.label.vo.TextImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文本数据导入：把 txt / docx / json / csv 解析成一篇可标注的正文并建条目。
 */
public interface TextImportService {

  /**
   * 导入单个文本文件。
   *
   * <p>一个文件生成一个条目 + 一个资产（正文落 MinIO），
   * 并解析出句边界写入资产元信息，供前端分页渲染与句级情感标注使用。
   *
   * @param meta 项目与批次信息
   * @param file 文本文件（≤ 20MB）
   */
  TextImportResultVO importFile(TextImportDTO meta, MultipartFile file);
}
