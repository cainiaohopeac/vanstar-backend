package org.dromara.label.service;

import java.io.InputStream;
import io.minio.StatObjectResponse;
import io.minio.messages.Part;

/**
 * MinIO 对象存储服务
 * <p>
 * 职责：
 * <ul>
 *   <li>流式上传（已知大小直接 PUT，size=-1 未知大小时 SDK 自动分片）</li>
 *   <li>服务端复制（大文件导入完成后复制到资产目录，无需回传）</li>
 *   <li>流式读取（文件网关 /uploads/** 使用）</li>
 *   <li>大文件分片上传（multipart）：createMultipartUpload / uploadPart / complete…</li>
 *   <li>按文件名推断 Content-Type</li>
 * </ul>
 */
public interface MinioService {

  /** 确保桶存在（幂等），启动迁移/上传前可调用。 */
  void ensureBucket();

  /** 上传字节流到 MinIO；size=-1 表示大小未知，SDK 自动分片。 */
  void putObject(String key, InputStream data, long size, String contentType);

  /** 服务端复制对象（同桶）。 */
  void copyObject(String sourceKey, String targetKey);

  /** 删除对象。 */
  void removeObject(String objectName);

  /** 打开对象的只读流（调用方负责关闭）。 */
  InputStream openObject(String key);

  /** 查询对象信息（大小/Content-Type）；对象不存在返回 null。 */
  StatObjectResponse statObject(String key);

  /** 创建分片上传，返回 uploadId。 */
  String createMultipartUpload(String objectName);

  /** 上传单个分片，返回 ETag。 */
  String uploadPart(String objectName, String uploadId, int partNumber, InputStream data, long size);

  /** 校验分片上传是否仍然有效（用于续传前检查）。 */
  boolean uploadExists(String objectName, String uploadId);

  /** 合并分片，完成上传。 */
  void completeMultipartUpload(String objectName, String uploadId, Part[] parts);

  /** 终止分片上传（删除未完成的分片）。 */
  void abortMultipartUpload(String objectName, String uploadId);

  /** 按文件名推断 Content-Type；未知类型返回 application/octet-stream。 */
  String contentTypeFor(String name);

  /**
   * 生成对象的预签名 GET 直链：浏览器可拿着它直连 MinIO 下载。
   *
   * <p>点云/参考图走这条直链就绕开了「网关 → 前端 → 网关 → 后端 JVM → MinIO」的逐字节转发，
   * 并发能力与带宽都交给对象存储，标注页预加载几百帧才不会把业务后端拖成瓶颈。</p>
   *
   * @param key    MinIO 对象名（如 uploads/a/b.pcd）
   * @param expiry 有效期，超过 7 天会被 MinIO 拒绝
   */
  String presignedGetUrl(String key, java.time.Duration expiry);
}
