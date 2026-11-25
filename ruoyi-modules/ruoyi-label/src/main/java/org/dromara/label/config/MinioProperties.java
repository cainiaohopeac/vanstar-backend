package org.dromara.label.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置属性，绑定 application.yml 中 minio.* 配置项
 */
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

  /** MinIO 服务地址，如 http://192.168.0.1:9000 */
  private String endpoint;

  /**
   * 浏览器可访问的 MinIO 地址（用于生成预签名直链）。
   *
   * <p>后端连 MinIO 走内网（如 192.168.0.162:9000），而浏览器只能访问公网入口
   * （如 http://60.217.79.104:9000，由接入层四层转发到内网 MinIO）。SigV4 签名会把 Host
   * 算进签名串，所以预签名 URL 必须用「浏览器实际访问的 Host」来计算，否则直接 403。</p>
   *
   * <p>留空则退回 {@link #endpoint}（本地/单机部署两者相同）。</p>
   */
  private String publicEndpoint;

  /** 访问密钥 */
  private String accessKey;

  /** 私有密钥 */
  private String secretKey;

  /** 导入文件存放的桶名 */
  private String bucket = "mark-server";

  /** 默认分片大小（字节），默认 10MB；Windows/防火墙环境下建议 local/dev 再降到 5MB */
  private long chunkSize = 10L * 1024 * 1024;

  /** 是否在启动时检查并自动创建桶。Demo 环境可关闭，避免 MinIO 未启动导致后端无法启动。 */
  private boolean initializeBucket = false;

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getPublicEndpoint() {
    return publicEndpoint;
  }

  public void setPublicEndpoint(String publicEndpoint) {
    this.publicEndpoint = publicEndpoint;
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

  public long getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(long chunkSize) {
    this.chunkSize = chunkSize;
  }

  public boolean isInitializeBucket() {
    return initializeBucket;
  }

  public void setInitializeBucket(boolean initializeBucket) {
    this.initializeBucket = initializeBucket;
  }
}
