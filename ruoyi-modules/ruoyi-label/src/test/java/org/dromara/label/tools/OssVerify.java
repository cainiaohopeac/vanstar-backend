package org.dromara.label.tools;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OSS S3 兼容性一次性验证工具（不进正式流程）。
 *
 * <p>用法: java OssVerify &lt;endpoint&gt; &lt;region&gt; &lt;ak&gt; &lt;sk&gt; &lt;bucket&gt;
 * 依次验证: 桶存在 → PUT → STAT → GET → 预签名 URL → 清理。</p>
 */
public final class OssVerify {

  public static void main(String[] args) throws Exception {
    String endpoint = args[0];
    String region = args[1];
    String ak = args[2];
    String sk = args[3];
    String bucket = args[4];
    String testKey = "_oss_verify_" + System.currentTimeMillis() + ".txt";

    MinioClient c = MinioClient.builder()
        .endpoint(endpoint).credentials(ak, sk).region(region).build();

    long t0 = System.currentTimeMillis();
    System.out.println("[1] bucketExists: "
        + c.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
    System.out.println("    " + (System.currentTimeMillis() - t0) + "ms");

    byte[] payload = "oss-verify-ok".getBytes(StandardCharsets.UTF_8);
    t0 = System.currentTimeMillis();
    try (ByteArrayInputStream in = new ByteArrayInputStream(payload)) {
      c.putObject(PutObjectArgs.builder().bucket(bucket).object(testKey)
          .stream(in, payload.length, -1).contentType("text/plain").build());
    }
    System.out.println("[2] PUT: ok, " + (System.currentTimeMillis() - t0) + "ms");

    t0 = System.currentTimeMillis();
    StatObjectResponse st = c.statObject(
        StatObjectArgs.builder().bucket(bucket).object(testKey).build());
    System.out.println("[3] STAT: size=" + st.size() + ", " + (System.currentTimeMillis() - t0) + "ms");

    t0 = System.currentTimeMillis();
    byte[] got;
    try (var is = c.getObject(GetObjectArgs.builder().bucket(bucket).object(testKey).build())) {
      got = is.readAllBytes();
    }
    System.out.println("[4] GET: content=" + new String(got, StandardCharsets.UTF_8)
        + ", " + (System.currentTimeMillis() - t0) + "ms");

    t0 = System.currentTimeMillis();
    String url = c.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
        .method(Method.GET).bucket(bucket).object(testKey)
        .expiry(300, TimeUnit.SECONDS).build());
    System.out.println("[5] presign: " + (System.currentTimeMillis() - t0) + "ms");
    System.out.println("    url=" + url);

    c.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(testKey).build());
    System.out.println("[6] cleanup: ok");
    System.out.println("RESULT=PASS");
  }
}
