# OCR 文本识别 —— 实现与部署方案

> 适用范围：图片标注工作台「画框/多边形 → 自动或手动 OCR 文本识别 → 结果写入标注对象」全链路。
> 关联仓库：`bzpt-backend`（本仓库，`ruoyi-label` 模块 + 部署配置）、`bz-frontend`（标注页交互）、`ocr-service`（PaddleOCR 引擎，部署于 187 机器）。
> 本文档与代码同步维护。

---

## 一、目标与约束

- **目标**：标注员在画布上画矩形 / 多边形后，一键（或自动）对框内区域做文字识别，识别结果可编辑并写入目标「文本属性」与结构化 `ocr` 详情，提升文本类标注效率。
- **设计约束**：
  - 前端**只传 `assetId` + 几何（不传原图）**，由后端从 MinIO 取原图并裁剪，原图不外流、避免大图网络传输。
  - 引擎与核心业务解耦：后端通过 `OcrEngine` 接口抽象，可切 `paddle`（真实） / `mock`（联调）。
  - 多边形支持「框外涂白」后再识别，避免背景干扰。

---

## 二、整体架构（三层）

```
标注画布(Vue)  ──POST /prod-api/ocr/recognize(assetId + geometry + options)──▶  Spring Boot(ruoyi-label)
                                                                                       │ 按 assetId 从 MinIO 取原图资源
                                                                                       │ 按几何裁剪(BBOX / 多边形 + 可选涂白)
                                                                                       ▼
                                                                         FastAPI PaddleOCR(127.0.0.1:8866)
                                                                                       │
                                                                                 PaddleOCR 2.9.1 (CPU)
```

**数据流**：

1. 前端把当前标注对象的几何换算为**原图像素坐标**的 BBOX / 多边形，连同 `assetId` 与识别选项（`language`、`useAngleCls`）发给后端。
2. 后端 `OcrService` 编排：`OcrMapper.selectAssetById(assetId)` 取资源图路径 → `ImageCropUtil` 裁剪 → 调引擎。
3. 引擎返回整段文本 `transcription` + 置信度 `confidence` + 逐行 `lines[]`；后端封装为 `OcrResultVO` 返回。
4. 前端弹 `OcrResultDialog`，用户可编辑文本、选择目标文本属性、查看置信度/引擎/语言/逐行结果，确认后**双写**：目标 `text` 属性 + 结构化 `ocr` 详情。

---

## 三、前端实现（`bz-frontend`）

| 文件 | 职责 |
|---|---|
| `src/api/label/ocr.ts` | 接口层：`recognizeOcr` → `POST /ocr/recognize`；定义 `OcrResultVO`、`OcrGeometry`（`BBOX` / `POLYGON`）、`OcrRecognizeReq`；含 `VITE_OCR_MOCK` 本地模拟兜底 |
| `src/composables/useOcrSettings.ts` | localStorage 持久化：自动识别开关、语言、多边形涂白、每个标签上次选中的文本属性 |
| `src/views/label/annotate/OcrResultDialog.vue` | 结果弹窗：可编辑文本、下拉选目标文本属性、置信度/引擎/语言展示、逐行结果、重新识别、确认写入 |
| `src/views/label/annotate/image.vue` | 接线：工具条「自动 OCR」开关 + 「识别」按钮 + 更多菜单「OCR 文本识别」；`buildOcrGeometry` 把标注对象几何转为原图像素坐标；`runOcrForObject` 组装请求；`maybeAutoOcr` 画完框/多边形自动触发；`confirmOcr` 双写 |

**确认写入的双写结构（`ocr` 详情字段）**：`version` / `shape` / `engine` / `transcription` / `language` / `confidence` / `lines` / `difficult` / `source` / `editedBy` / `recognizedAt`。

---

## 四、后端实现（`bzpt-backend` · `ruoyi-modules/ruoyi-label/.../label/ocr`）

14 个 Java 类：

| 类 | 职责 |
|---|---|
| `OcrController` | `POST /ocr/recognize`（无 `/label` 前缀，与前端 `/prod-api/ocr/recognize` 对齐），入参 `OcrRecognizeReq` |
| `OcrService` | 编排：取资源图 → 裁剪 → 调引擎 → 封装 `OcrResultVO` |
| `OcrConfig` / `OcrProperties` | 绑定 `ocr` yml 段，提供 `enabled`/`provider`/`endpoint`/`timeoutMs`/`minBoxSize`/`paddingRatio`/`language`/`useAngleCls`/`maskOutsidePolygon` |
| `support/OcrEngine` | 引擎抽象接口：`recognize(BufferedImage, language, useAngleCls) → OcrResultVO` |
| `support/HttpOcrEngine` | HTTP 调 FastAPI `/predict/ocr`（multipart 图片 + 表单 `language` / `use_angle_cls`），`enabled=false` 时直接返回「未启用」 |
| `support/MockOcrEngine` | 离线联调返回示例文本 |
| `support/ImageCropUtil` | BBOX 直接裁；多边形裁 + 可选「框外涂白」；含外扩 `paddingRatio` 与 `minBoxSize` 过滤 |
| `dto/OcrRecognizeReq` / `OcrGeometryDTO` / `OcrOptionsDTO` / `AssetImageRef` | 请求入参与资源引用 |
| `vo/OcrResultVO` / `vo/OcrLineVO` | 返回结构（与前端 `OcrResultVO` 对齐） |
| `OcrMapper.java` + `OcrMapper.xml` | 按 `assetId` 查询资源图 |

**路由说明**：控制器显式映射 `POST /ocr/recognize`，不挂 `/label` 前缀，确保与前端代理路径 `/prod-api/ocr/recognize` 对齐。

---

## 五、OCR 引擎服务（`ocr-service`）

- **测试环境**：部署于 187 机器，监听 `127.0.0.1:8866`（与后端同机）。
- **生产环境**：部署于 192.168.0.223（2026-11 从 192.168.0.162 迁移，162 已回收），systemd 服务 `paddle-ocr`，监听 `0.0.0.0:8866`，两台应用机（32/211）远程调用，后端配置 `endpoint: http://192.168.0.223:8866/predict/ocr`。迁移方式：整目录（含 `.venv`）+ `/root/.paddleocr` 模型缓存打包拷贝，两机同为 openEuler 24.03 / Python 3.11.6，venv 可直接复用；仍需 `LD_PRELOAD=/usr/lib64/libz.so.1`。

- **框架**：FastAPI `app.py`
- **接口**：
  - `POST /predict/ocr`：multipart `image` + 表单 `language` / `use_angle_cls` → `{ success, engine, language, transcription, confidence, cost_ms, lines[{ transcription, points, score }] }`
  - `GET /health`：健康检查，返回 `{ paddleocr: true }`
- **依赖**：PaddleOCR 2.9.1 + PaddlePaddle 2.6.2（CPU），`opencv-python-headless`
- **运行方式**：`systemd` 服务 `paddle-ocr` 常驻（监听 `127.0.0.1:8866`）
- **部署踩坑修复**：
  - 缺 `libGL` → 改用 `opencv-python-headless`
  - paddle 与系统 `libz` 冲突 → 启动前 `LD_PRELOAD=/usr/lib64/libz.so.1`
  - Python 环境：`/root/bzpt-deploy/ocr-service/.venv`

---

## 六、部署拓扑（187 机器）

| 组件 | 地址 / 端口 | 说明 |
|---|---|---|
| FastAPI PaddleOCR | `127.0.0.1:8866` | `systemd` 服务 `paddle-ocr` active |
| 后端 `bzpt-server`（Spring Boot） | 容器内 host 网络 **8081** | `/ocr/recognize` 在此暴露 |
| 前端 `bzpt-web` | 容器内 **8089** | 经本机 nginx 反代 |
| 对外网关 nginx | **8088** | `/prod-api/` → 后端 8081；`/uploads/` → 后端回源（MinIO 流式）；`/` → 前端 8089 |

> 网关配置见 `deploy/prod/conf/nginx-gw.conf`：`/prod-api/` 与 `/uploads/` 均 `proxy_pass` 到后端 upstream；`/uploads/` 的 `proxy_pass` 不带 URI 以保留原始路径（后端 `FileAccessController` 映射 `/uploads/**`）。

---

## 七、配置说明（`ocr` yml 段）

位于 6 个 yml：`ruoyi-admin/src/main/resources/application-{dev,local,prod}.yml` 与 `deploy/prod/conf/application-{dev,local,prod}.yml`：

```yaml
ocr:
  # 总开关：false 时接口直接返回「未启用」，不调用任何引擎
  enabled: true
  # 引擎提供方：paddle(HTTP 调 PaddleOCR) / mock(示例文本，便于无引擎联调)
  provider: paddle
  # PaddleOCR 服务地址（provider=paddle 时必填）
  endpoint: http://127.0.0.1:8866/predict/ocr
  # 调用超时(ms)
  timeout-ms: 10000
  # 小于该尺寸的框不识别(像素)
  min-box-size: 32
  # 裁剪外扩比例(避免切到文字边缘)
  padding-ratio: 0.08
  # 默认识别语言
  language: ch
  # 是否启用方向分类(角度校正)
  use-angle-cls: true
  # 多边形：是否将框外区域涂白后再识别
  mask-outside-polygon: true
```

| 字段 | 默认值 | 作用 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `provider` | `paddle` | 引擎实现选择 |
| `endpoint` | `http://127.0.0.1:8866/predict/ocr` | PaddleOCR 服务地址 |
| `timeout-ms` | `10000` | 引擎调用超时 |
| `min-box-size` | `32` | 过小的框跳过识别 |
| `padding-ratio` | `0.08` | 裁剪外扩比例 |
| `language` | `ch` | 默认语言（前端可在弹窗切换） |
| `use-angle-cls` | `true` | 角度校正 |
| `mask-outside-polygon` | `true` | 多边形框外涂白 |

---

## 八、接口契约

**请求** `POST /ocr/recognize`：

```json
{
  "assetId": 123,
  "geometry": { "type": "BBOX", "rings": [[[x1,y1],[x2,y2],[x3,y3],[x4,y4]]] },
  "options": { "language": "ch", "useAngleCls": true }
}
```

**响应** `Result<OcrResultVO>`：

```json
{
  "code": 200,
  "data": {
    "transcription": "HELLO OCR 123",
    "confidence": 0.9764,
    "language": "ch",
    "engine": "paddle",
    "difficult": false,
    "lines": [
      { "transcription": "HELLO OCR 123", "score": 0.9764, "points": [[...]] }
    ]
  }
}
```

---

## 九、运维与验证

**健康检查**：

```bash
curl -s http://127.0.0.1:8866/health        # 引擎
systemctl is-active paddle-ocr              # 引擎进程
curl -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8081/ocr/recognize  # 端点已注册(需鉴权→401)
```

**重启后端使 OCR 配置生效**：

```bash
docker restart bzpt-server
```

**真实识别验证（绕过 Spring Boot 直测引擎）**：

```bash
# 生成含文字图片 → 直调 FastAPI
python - <<'PY'
import cv2, numpy as np
img = np.full((110, 560, 3), 255, np.uint8)
cv2.putText(img, 'HELLO OCR 123', (10, 70), cv2.FONT_HERSHEY_SIMPLEX, 1.6, (0,0,0), 3)
cv2.imwrite('/tmp/test_ocr.png', img)
PY
curl -s -F 'image=@/tmp/test_ocr.png' -F 'language=ch' -F 'use_angle_cls=true' http://127.0.0.1:8866/predict/ocr
# → {"transcription":"HELLO OCR 123","confidence":0.9764,"cost_ms":203,...}
```

**已验证结论**：

- ✅ 引擎真实识别：直调 FastAPI 返回 `transcription:"HELLO OCR 123"`、`confidence:0.9764`、`cost_ms:203`。
- ✅ 后端接线：挂载的 `ocr` 配置 `provider=paddle`、endpoint 正确；`/ocr/recognize` 端点已注册且鉴权生效（返回 `401 认证失败`）。
- ✅ 前端：类型检查与 `build:dev` 通过，dev server 正常启动，`OcrResultDialog` 经 Vite 转换无报错。

**已知限制 / 后续**：

- ⚠️ 完整的「前端 → 后端（带 Sa-Token 鉴权）/ocr/recognize → 引擎」链路尚未在命令行实跑（登录需验证码，无法无浏览器拿令牌）。建议在浏览器登录后画框实测一次。
- ⚠️ 若后端以多实例部署（见 `nginx-gw.conf` upstream），OCR 为无状态调用，引擎地址在各实例本地 `127.0.0.1:8866`，需保证每台实例机器都部署了 `ocr-service`。
- 多边形涂白、角度分类等策略可在 yml 调参，无需改代码。
