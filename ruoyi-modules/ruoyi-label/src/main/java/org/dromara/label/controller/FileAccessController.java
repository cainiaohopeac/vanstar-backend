package org.dromara.label.controller;

import org.dromara.label.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.label.dto.PointCloudBatchRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件访问网关：/uploads/** 从 MinIO 对象存储流式返回。
 * <p>
 * MinIO 为唯一文件源后，资产文件的对外 URL 契约保持不变（/uploads/...），
 * 由本控制器把请求路径映射为 MinIO 对象 key（去掉前导 / 即对象名），
 * 前端标注页的 &lt;img&gt;、点云 fetch、下载链接均无需改动。
 */
@RestController
public class FileAccessController {

  /**
   * 批量取帧前的 stat 并发池。
   *
   * <p>串行 stat 是首字节延迟的大头：本地实测 180 帧要 1.6 秒才开始吐数据，
   * 公网每次往返更久。并发 stat 后总耗时≈最慢的一次往返。</p>
   *
   * <p>线程数固定 16：再多会把 MinIO/后端线程打满，收益不增。</p>
   */
  private static final ExecutorService STAT_POOL = Executors.newFixedThreadPool(16, r -> {
    Thread t = new Thread(r, "pc-batch-stat");
    t.setDaemon(true);
    return t;
  });

  @Autowired
  private MinioService minioService;

  @GetMapping("/uploads/**")
  public void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    String objectKey = path.startsWith("/") ? path.substring(1) : path;
    StatObjectResponse info = minioService.statObject(objectKey);
    if (info == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "file not found: " + path);
      return;
    }
    String contentType = info.contentType();
    if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
      // 上传时未设置/推断失败的兜底：按文件名再推断一次，保证浏览器可直接渲染图片
      contentType = minioService.contentTypeFor(objectKey);
    }
    response.setContentType(contentType);
    response.setContentLengthLong(info.size());
    try (InputStream in = minioService.openObject(objectKey);
        OutputStream out = response.getOutputStream()) {
      in.transferTo(out);
    }
  }

  /** 批量取帧的单次上限：防止一次请求无限大把连接和内存拖垮，超出的帧仍按单帧网关获取。 */
  private static final int MAX_BATCH_FRAMES = 500;
  /** 容器魔数：前端据此判断自己拿到的是「批量帧容器」还是意外的 JSON/HTML。 */
  private static final byte[] BATCH_MAGIC = "PCFB".getBytes(StandardCharsets.US_ASCII);

  /**
   * 批量取点云帧：一次请求按序返回多帧字节流，前端流式接收、攒满一帧就解析一帧，
   * 切帧不再需要「每帧一次 HTTP」。点云帧通常只有几 MB 但帧数多，逐个下发时浏览器
   * 同域名只有 6 条并发、剩下的全在排队，还会叠加网关鉴权开销 —— 这是切帧慢的主因。
   * <p>
   * 响应容器（便于边下边解析，不用等整包）：
   * <pre>[4B 魔数 "PCFB"][4B manifest 长度(BE)][manifest JSON][帧1字节][帧2字节]...</pre>
   * manifest 为 {@code {"lens":[len1,len2,...]}}，与请求 urls 一一对应；len=0 表示该帧对象不存在。
   * 调用方把「当前帧」放在首位即可优先拿到数据、先行渲染。
   */
  @PostMapping("/uploads/point-cloud-batch")
  public void servePointCloudBatch(@RequestBody(required = false) PointCloudBatchRequest payload,
      HttpServletResponse response) throws IOException {
    List<String> urls = toStringList(payload == null ? null : payload.getUrls());
    if (urls.isEmpty() || urls.size() > MAX_BATCH_FRAMES) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "urls empty or too many (max " + MAX_BATCH_FRAMES + ")");
      return;
    }
    // 先把对象名换成 key（纯字符串处理，零网络）
    List<String> keys = new ArrayList<>(urls.size());
    for (String url : urls) {
      keys.add(toObjectKey(url));
    }
    // 再并发 stat 拿每帧长度做 manifest（前端据此切帧）。
    // 串行时 180 帧就是一个接一个的往返，本地实测首字节被拖到 1.6 秒；
    // 并发后总耗时≈最慢的那一次，首帧能立刻开始回传。
    long[] lens = new long[keys.size()];
    List<CompletableFuture<Void>> stats = new ArrayList<>(keys.size());
    for (int i = 0; i < keys.size(); i++) {
      final int idx = i;
      final String key = keys.get(idx);
      if (key == null) {
        continue;
      }
      stats.add(CompletableFuture.runAsync(() -> {
        try {
          StatObjectResponse info = minioService.statObject(key);
          if (info != null) {
            lens[idx] = info.size();
          }
        } catch (RuntimeException ignore) {
          // 单个对象 stat 失败按 0 字节处理：该帧由前端按需补拉
        }
      }, STAT_POOL));
    }
    CompletableFuture.allOf(stats.toArray(new CompletableFuture[0])).join();
    long total = 0;
    for (long len : lens) {
      total += len;
    }
    byte[] manifest = buildManifest(lens);
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setHeader("X-Frame-Count", String.valueOf(lens.length));
    response.setContentLengthLong(BATCH_MAGIC.length + 4L + manifest.length + total);
    try (OutputStream out = response.getOutputStream()) {
      out.write(BATCH_MAGIC);
      writeIntBE(out, manifest.length);
      out.write(manifest);
      out.flush();
      for (int i = 0; i < keys.size(); i++) {
        if (keys.get(i) == null || lens[i] <= 0) {
          continue;
        }
        try (InputStream in = minioService.openObject(keys.get(i))) {
          // 64KB 缓冲：JDK transferTo 默认 8KB，一帧 3MB 要写近 400 次，
          // 180 帧就是七万多次系统调用，传输阶段白白多吃几秒。
          byte[] buf = new byte[64 * 1024];
          int n;
          while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
          }
        }
        // 每帧写完即刷：不等整批攒满再回，前端才能边下边解析（当前帧排首位时几乎立刻开渲）
        out.flush();
      }
    }
  }

  /**
   * 批量签发点云/图片的 MinIO 预签名直链：前端拿链接**直连对象存储**并发下载，
   * 不再让几百帧的字节流穿一遍业务后端（JVM 线程 + 单机出口带宽才是预加载慢的真正瓶颈）。
   *
   * <p>返回 {@code {"urls":[{"src":"/uploads/a.bin","url":"http://host:9000/mark-server/..."}]}}，
   * url 为 null 表示该对象签名失败（缺失/存储异常），前端据此回退到
   * {@link #servePointCloudBatch} 的批量通道。</p>
   */
  @PostMapping("/uploads/presign")
  public Map<String, Object> presign(@RequestBody(required = false) PointCloudBatchRequest payload) {
    List<String> urls = toStringList(payload == null ? null : payload.getUrls());
    List<Map<String, String>> items = new ArrayList<>(urls.size());
    for (String url : urls) {
      String key = toObjectKey(url);
      String signed = null;
      if (key != null) {
        try {
          // 2 小时：足够覆盖一次连续标注；过期后前端重新进入页面会再签一次。
          // signing client 是主存储对应的签名客户端（oss.enabled 决定 OSS 或 MinIO）
          signed = minioService.presignedGetUrl(key, Duration.ofHours(2));
        } catch (RuntimeException ignore) {
          // 单个对象签名失败不拖累整批：前端见 null 会走批量接口兜底
          signed = null;
        }
      }
      Map<String, String> item = new LinkedHashMap<>(2);
      item.put("src", url);
      item.put("url", signed);
      items.add(item);
    }
    Map<String, Object> body = new LinkedHashMap<>(2);
    body.put("urls", items);
    return body;
  }

  /** 请求里的 urls 清洗一下：去空白项顺便 trim，避免对象名带空格导致 stat 落空。 */
  private static List<String> toStringList(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return new ArrayList<>();
    }
    List<String> out = new ArrayList<>(raw.size());
    for (String item : raw) {
      String s = item == null ? "" : item.trim();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }

  /**
   * 取帧地址转 MinIO 对象名：{@code /uploads/a/b.pcd} → {@code uploads/a/b.pcd}；
   * 完整 URL（带 scheme/host）先剥掉域名部分，保证前端传什么形态都能命中同一个对象。
   */
  private static String toObjectKey(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String path = raw.trim();
    int scheme = path.indexOf("://");
    if (scheme > 0) {
      int slash = path.indexOf('/', scheme + 3);
      if (slash > 0) {
        path = path.substring(slash);
      }
    }
    int mark = path.indexOf("/uploads/");
    if (mark >= 0) {
      path = path.substring(mark + 1);
    } else if (path.startsWith("/")) {
      path = path.substring(1);
    }
    return path.isEmpty() ? null : path;
  }

  /** 帧长清单：{"lens":[...]}（手写避免为一个小结构引入序列化依赖）。 */
  private static byte[] buildManifest(long[] lens) {
    StringBuilder sb = new StringBuilder("{\"lens\":[");
    for (int i = 0; i < lens.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(lens[i]);
    }
    sb.append("]}");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void writeIntBE(OutputStream out, int value) throws IOException {
    out.write((value >>> 24) & 0xFF);
    out.write((value >>> 16) & 0xFF);
    out.write((value >>> 8) & 0xFF);
    out.write(value & 0xFF);
  }
}
