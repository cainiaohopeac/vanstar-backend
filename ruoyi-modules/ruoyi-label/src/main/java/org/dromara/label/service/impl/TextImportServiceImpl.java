package org.dromara.label.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.AssetParseStatus;
import org.dromara.label.common.MediaType;
import org.dromara.label.dto.TextImportDTO;
import org.dromara.label.entity.LabelAsset;
import org.dromara.label.entity.LabelDataEntry;
import org.dromara.label.entity.LabelUpload;
import org.dromara.label.mapper.TextImportMapper;
import org.dromara.label.parser.SentenceSplitter;
import org.dromara.label.parser.TextFileParser;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.TextImportService;
import org.dromara.label.vo.TextImportResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文本导入实现：解析 → 分句 → 建条目与资产。
 *
 * <p>一个文件 = 一个条目 + 一个资产。之所以不按句/段拆成多个资产：
 * 实体标注用<strong>全文级字符偏移</strong>，且业务上允许实体嵌套、后续可能放开跨句，
 * 拆成多资产会让偏移失去统一基准，导出 BIO 时还要再做一层拼接映射。
 */
@Service
public class TextImportServiceImpl extends BaseService implements TextImportService {

  /** 条目编号前缀 */
  private static final String ENTRY_NO_PREFIX = "TXT";

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final TextImportMapper textImportMapper;
  private final MinioService minioService;

  public TextImportServiceImpl(TextImportMapper textImportMapper, MinioService minioService) {
    this.textImportMapper = textImportMapper;
    this.minioService = minioService;
  }

  @Override
  @Transactional
  public TextImportResultVO importFile(TextImportDTO meta, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ServiceException("上传文件为空");
    }
    String originalName = file.getOriginalFilename() == null ? "text.txt" : file.getOriginalFilename();
    if (!TextFileParser.isSupported(originalName)) {
      throw new ServiceException("不支持的文本格式：" + originalName + "（支持 txt/docx/json/csv）");
    }
    if (file.getSize() > TextFileParser.MAX_FILE_SIZE) {
      throw new ServiceException("文件大小超过 20MB 限制");
    }

    // 1) 解析成纯文本（docx 走 POI，json/csv 按约定取正文列）
    String rawText;
    try (InputStream in = file.getInputStream()) {
      rawText = TextFileParser.parse(in, originalName);
    } catch (IOException e) {
      throw new ServiceException("文件读取失败：" + e.getMessage());
    } catch (IllegalArgumentException e) {
      throw new ServiceException(e.getMessage());
    }
    if (rawText == null || rawText.isBlank()) {
      throw new ServiceException("未能从文件中解析出任何正文");
    }

    // 2) 规范化换行：统一成 \n，避免 \r\n 让字符偏移与前端选区对不上
    String text = rawText.replace("\r\n", "\n").replace('\r', '\n');

    // 3) 分句：句边界用于前端分页渲染、句级情感标注与审核跳转
    List<int[]> sentences = SentenceSplitter.split(text);

    // 未指定批次时归入项目默认批次：否则 entry/asset 的 batch_id 为 NULL，
    // 任务按批次下发时统计不到这些文本，作业会直接建不出来
    Long batchId = resolveBatchId(meta.getProjectId(), meta.getBatchId());

    // 4) 正文落 MinIO（以规范化后的文本为准，保证偏移与存储内容一致）
    long userId = currentUserId();
    String fileUrl = "/uploads/" + meta.getProjectId() + "/text/" + LocalDate.now().format(DATE_FMT)
        + "/" + UUID.randomUUID() + ".txt";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      minioService.putObject(fileUrl.substring(1), in, bytes.length, "text/plain;charset=utf-8");
    } catch (IOException e) {
      throw new ServiceException("正文上传失败：" + e.getMessage());
    }

    // 5) 建上传记录：label_data_entry.upload_id 非空且无默认值，不先建这条记录，
    //    后面插条目会被数据库直接拒绝；导入记录页也依赖本表展示历史
    LabelUpload upload = new LabelUpload();
    upload.setProjectId(meta.getProjectId());
    upload.setBatchId(batchId);
    upload.setUploadNo("UP" + LocalDate.now().format(DATE_FMT) + "-"
        + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    upload.setFileName(originalName);
    upload.setFileSize((long) bytes.length);
    upload.setObjectName(fileUrl);
    upload.setSourceType(MediaType.TEXT);
    upload.setAssetCount(1);
    upload.setStatus(AssetParseStatus.SUCCESS);
    upload.setCreateBy(userId);
    upload.setUpdateBy(userId);
    textImportMapper.insertUpload(upload);

    // 6) 建条目（先建，拿到 id 再回填资产指针）
    LabelDataEntry entry = new LabelDataEntry();
    entry.setProjectId(meta.getProjectId());
    entry.setBatchId(batchId);
    entry.setUploadId(upload.getId());
    entry.setEntryNo(newEntryNo());
    entry.setEntryName(originalName);
    entry.setSourceFile(originalName);
    entry.setSeqNo(1);
    entry.setAssetCount(1);
    entry.setSplitMode("whole");
    entry.setMediaType(MediaType.TEXT);
    entry.setParseStatus(AssetParseStatus.SUCCESS);
    entry.setLabelStatus("pending");
    entry.setCreateBy(userId);
    entry.setUpdateBy(userId);
    textImportMapper.insertEntry(entry);

    // 7) 建资产：句边界以扁平数组 [s0,e0,s1,e1,...] 存储，比对象数组省一半以上体积
    //    （长文本几万句时差异明显，且加载时要整段读出）
    LabelAsset asset = new LabelAsset();
    asset.setProjectId(meta.getProjectId());
    asset.setBatchId(batchId);
    asset.setUploadId(upload.getId());
    asset.setEntryId(entry.getId());
    asset.setMediaType(MediaType.TEXT);
    asset.setFileName(originalName);
    asset.setFileUrl(fileUrl);
    asset.setFileSize((long) bytes.length);
    asset.setParseStatus(AssetParseStatus.SUCCESS);
    asset.setMetaJson(buildMetaJson(text.length(), sentences));
    asset.setCreateBy(userId);
    asset.setUpdateBy(userId);
    textImportMapper.insertAsset(asset);

    textImportMapper.bindAssetEntry(asset.getId(), entry.getId(), userId);
    textImportMapper.updateEntryAssetInfo(entry.getId(), 1, asset.getId(), userId);

    TextImportResultVO result = new TextImportResultVO();
    result.setEntryId(entry.getId());
    result.setAssetId(asset.getId());
    result.setEntryNo(entry.getEntryNo());
    result.setEntryName(originalName);
    result.setFileName(originalName);
    result.setCharCount(text.length());
    result.setSentenceCount(sentences.size());
    result.setParseStatus(AssetParseStatus.SUCCESS);
    return result;
  }

  /** 条目编号：TXT + 日期 + 随机短码，保证唯一且可读。 */
  private String newEntryNo() {
    return ENTRY_NO_PREFIX + LocalDate.now().format(DATE_FMT) + "-"
        + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
  }

  /**
   * 资产元信息：字数、句数与句边界。
   *
   * <p>句边界用扁平数组 {@code [s0,e0,s1,e1,...]} 而不是对象数组
   * {@code [{index,start,end}]}：同样的信息量体积小一半以上，
   * 长文本几万句时该字段会达到数百 KB，省下来的都是每次加载要读的字节。
   */
  private String buildMetaJson(int charCount, List<int[]> sentences) {
    List<Integer> bounds = new ArrayList<>(sentences.size() * 2);
    for (int[] bound : sentences) {
      bounds.add(bound[0]);
      bounds.add(bound[1]);
    }
    StringBuilder sb = new StringBuilder(64 + bounds.size() * 8);
    sb.append("{\"charCount\":").append(charCount)
        .append(",\"sentenceCount\":").append(sentences.size())
        .append(",\"bounds\":[");
    for (int i = 0; i < bounds.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(bounds.get(i));
    }
    sb.append("]}");
    return sb.toString();
  }
}
