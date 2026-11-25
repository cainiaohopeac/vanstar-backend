package org.dromara.label.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（点云存储迁移目标）。
 *
 * <p>背景：现网 MinIO 挂在联通单线机房（60.217.79.104），移动/电信用户跨网访问
 * 丢包严重（实测 TCP 连接失败率 40%，30MB 下载要 7~8 分钟）。OSS 是 BGP 多线接入，
 * 任何运营商的浏览器直链下载都稳定在 1 秒档。</p>
 *
 * <p>OSS 全面兼容 S3 协议，复用 MinIO SDK，无需引入阿里云专有 SDK。</p>
 */
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

  /** 迁移总开关：false 时后端行为与迁移前完全一致（presign 只签 MinIO） */
  private boolean enabled = false;

  /** OSS Endpoint，如 https://oss-cn-beijing.aliyuncs.com */
  private String endpoint;

  private String accessKey;

  private String secretKey;

  /** 桶名（与 MinIO 同名，对象 key 完全一致） */
  private String bucket = "mark-server";

  /**
   * SigV4 的 region 字符串。OSS 对该值的校验较宽松，默认 cn-beijing；
   * 签名 403（SignatureDoesNotMatch/AuthorizationHeaderMalformed）时按 OSS
   * 控制台提示调整为 oss-cn-beijing 等。
   */
  private String region = "cn-beijing";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }
}
