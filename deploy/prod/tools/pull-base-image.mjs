#!/usr/bin/env node
// ============================================================
// 不依赖 docker CLI 的基础镜像拉取工具
//   直连 OCI/Docker Registry HTTP API 下载 manifest + config + layers,
//   组装成标准的 `docker save` 格式目录并打包 tar.gz(可直接 docker load -i)
//
// 用法:
//   node pull-base-image.mjs --image eclipse-temurin:17-jre-jammy --out base.tgz
//   node pull-base-image.mjs --image library/nginx:stable-alpine --registry docker.m.daocloud.io --out nginx.tgz
//
// 参数:
//   --image      必填, 仓库:标签(registry 判断: 首段含 . 或 : 则视为镜像站域名)
//   --out        输出 tar.gz 路径(默认 ./<repo>-<tag>.tar.gz)
//   --registry   registry 域名(默认 registry-1.docker.io; 国内不通可换镜像站)
//   --arch       平台架构(默认 amd64)
//   --os         平台系统(默认 linux)
// ============================================================
import { mkdirSync, rmSync, writeFileSync, createWriteStream, createReadStream, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { createGunzip, createGzip } from 'node:zlib';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';
import { tmpdir } from 'node:os';

const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : def;
};

const IMAGE = opt('image');
if (!IMAGE) {
  console.error('缺少参数 --image (例如 eclipse-temurin:17-jre-jammy)');
  process.exit(1);
}
const ARCH = opt('arch', 'amd64');
const PLATFORM_OS = opt('os', 'linux');

const ACCEPT = [
  'application/vnd.oci.image.manifest.v1+json',
  'application/vnd.oci.image.index.v1+json',
  'application/vnd.docker.distribution.manifest.v2+json',
  'application/vnd.docker.distribution.manifest.list.v2+json',
  'application/vnd.docker.distribution.manifest.v1+json'
].join(', ');

// 解析镜像名: 仅当存在多段路径且首段是域名(含 . 或 : 或 localhost)时才视为 registry
//   例: docker.m.daocloud.io/library/nginx:stable-alpine -> registry=docker.m.daocloud.io, repo=library/nginx
//   eclipse-temurin:17-jre-jammy                         -> registry 取 --registry, repo=library/eclipse-temurin
const parts = IMAGE.split('/');
const headIsHost = parts.length > 1 && (parts[0].includes('.') || parts[0].includes(':') || parts[0] === 'localhost');
let registry;
let repo;
if (headIsHost) {
  registry = opt('registry', parts[0]);
  repo = parts.slice(1).join('/');
} else {
  registry = opt('registry', 'registry-1.docker.io');
  repo = IMAGE;
}
if (!repo.includes('/')) repo = `library/${repo}`; // docker.io 官方镜像需补 library/ 前缀
const [repoName, tag = 'latest'] = repo.split(':');
const out = opt('out', `${repoName.replace(/\//g, '-')}-${tag}.tar.gz`);

const log = (m) => console.error(`[pull] ${m}`);

// ---------- registry 认证 ----------
let token = null;
async function fetchToken() {
  const probe = await fetch(`https://${registry}/v2/`);
  const wa = probe.headers.get('www-authenticate') || '';
  if (!/bearer/i.test(wa)) return null;
  const realm = /realm="([^"]+)"/.exec(wa)?.[1];
  if (!realm) return null;
  const service = /service="([^"]+)"/.exec(wa)?.[1];
  const u = new URL(realm);
  u.searchParams.set('scope', `repository:${repoName}:pull`);
  if (service) u.searchParams.set('service', service);
  const r = await fetch(u);
  if (!r.ok) return null;
  const j = await r.json();
  return j.token || j.access_token || null;
}

// 走国内镜像站时网络抖动常见, 统一带重试与超时
async function withRetry(fn, label, times = 3) {
  let err;
  for (let i = 1; i <= times; i++) {
    try {
      return await fn();
    } catch (e) {
      err = e;
      log(`${label} 第 ${i}/${times} 次失败: ${e.message}`);
      await new Promise((r) => setTimeout(r, 1500 * i));
    }
  }
  throw err;
}

const FETCH_OPTS = { signal: AbortSignal.timeout(900000) }; // 单请求 15 分钟上限

async function get(url, accept) {
  return withRetry(async () => {
    const headers = {};
    if (accept) headers.Accept = accept;
    let r = await fetch(url, {
      ...FETCH_OPTS,
      headers: token ? { ...headers, Authorization: `Bearer ${token}` } : headers
    });
    if (r.status === 401 && !token) {
      token = await fetchToken();
      if (!token) throw new Error(`registry 需要认证且获取 token 失败: ${url}`);
      r = await fetch(url, { ...FETCH_OPTS, headers: { ...headers, Authorization: `Bearer ${token}` } });
    }
    if (!r.ok) throw new Error(`GET ${url} 失败: HTTP ${r.status} ${r.statusText}`);
    return r;
  }, `GET ${url}`);
}

// ---------- 下载 manifest ----------
async function fetchManifest(ref) {
  const r = await get(`https://${registry}/v2/${repoName}/manifests/${ref}`, ACCEPT);
  return r.json();
}

log(`镜像 ${registry}/${repoName}:${tag} (${PLATFORM_OS}/${ARCH})`);
let manifest = await fetchManifest(tag);

// 多架构 index / manifest list -> 选目标平台
if (Array.isArray(manifest.manifests)) {
  const hit = manifest.manifests.find(
    (m) => m.platform && m.platform.architecture === ARCH && m.platform.os === PLATFORM_OS
  );
  if (!hit) throw new Error(`未找到 ${PLATFORM_OS}/${ARCH} 平台的镜像, 可用: ${JSON.stringify(manifest.manifests.map((m) => m.platform))}`);
  log(`多架构镜像 -> 选中 ${hit.digest}`);
  manifest = await fetchManifest(hit.digest);
}
if (!Array.isArray(manifest.layers)) throw new Error('manifest 结构异常, 缺少 layers');

// ---------- 组装 docker save 目录 ----------
const work = join(tmpdir(), `bzpt-pull-${Date.now()}`);
const root = join(work, 'root');
rmSync(work, { recursive: true, force: true });
mkdirSync(root, { recursive: true });

const hex = (d) => d.replace(/^sha256:/, '');

const saveBlob = async (digest, dest) => {
  const r = await get(`https://${registry}/v2/${repoName}/blobs/${digest}`, ACCEPT);
  await pipeline(Readable.fromWeb(r.body), createWriteStream(dest));
};

// config
const cfgHex = hex(manifest.config.digest);
const cfgPath = join(root, `${cfgHex}.json`);
log(`下载 config ${cfgHex.slice(0, 12)} ...`);
await saveBlob(manifest.config.digest, cfgPath);
const config = JSON.parse(await (await import('node:fs/promises')).readFile(cfgPath, 'utf8'));

// layers
const layerPaths = [];
const history = Array.isArray(config.history) ? config.history : [];
for (let i = 0; i < manifest.layers.length; i++) {
  const layer = manifest.layers[i];
  const lHex = hex(layer.digest);
  const dir = join(root, lHex);
  mkdirSync(dir, { recursive: true });
  const tmp = join(work, `${i}.blob`);
  log(`下载 layer ${i + 1}/${manifest.layers.length} ${lHex.slice(0, 12)} (${(layer.size / 1048576).toFixed(1)} MB)`);
  await saveBlob(layer.digest, tmp);

  const isGzip = /gzip|\+gzip/i.test(layer.mediaType) || layer.mediaType === 'application/vnd.oci.image.layer.v1.tar+gzip';
  const isZstd = /zstd/i.test(layer.mediaType);
  if (isZstd) throw new Error('zstd 压缩层暂不支持, 请改用 gzip 层的镜像标签');
  const dest = join(dir, 'layer.tar');
  if (isGzip) {
    const { createReadStream } = await import('node:fs');
    await pipeline(createReadStream(tmp), createGunzip(), createWriteStream(dest));
  } else {
    const { copyFileSync } = await import('node:fs');
    copyFileSync(tmp, dest);
  }
  rmSync(tmp, { force: true });

  // legacy v1 元数据(与 docker save 产物保持一致, 提高 docker load 兼容性)
  const created = history[i]?.created || config.created || new Date(0).toISOString();
  writeFileSync(join(dir, 'json'), JSON.stringify({ id: lHex, created, container_config: { Cmd: null } }));
  writeFileSync(join(dir, 'VERSION'), '1.0');
  layerPaths.push(`${lHex}/layer.tar`);
}

// manifest.json(docker save 的核心索引)
writeFileSync(
  join(root, 'manifest.json'),
  JSON.stringify([
    {
      Config: `${cfgHex}.json`,
      RepoTags: [`${repoName}:${tag}`],
      Layers: layerPaths
    }
  ])
);
// repositories(docker load 用它还原 repo:tag)
writeFileSync(
  join(root, 'repositories'),
  JSON.stringify({ [repoName]: { [tag]: layerPaths.length ? hex(manifest.layers[manifest.layers.length - 1].digest) : cfgHex } })
);

// 自己写 USTAR + gzip: 避免依赖系统 tar(Windows bsdtar 路径/参数行为不一致)
function ustarHeader(name, size, mode = 0o644) {
  const buf = Buffer.alloc(512);
  const put = (str, off, len) => buf.write(String(str), off, len, 'ascii');
  buf.write(name, 0, 100, 'utf8');
  put((mode & 0o7777).toString(8).padStart(7, '0') + '\0', 100, 8); // mode
  put('0000000\0', 108, 8);                                          // uid
  put('0000000\0', 116, 8);                                          // gid
  put(size.toString(8).padStart(11, '0') + '\0', 124, 12);           // size
  put(Math.floor(Date.now() / 1000).toString(8).padStart(11, '0') + '\0', 136, 12); // mtime
  buf.write('        ', 148, 8, 'ascii');                            // chksum 占位(8 空格)
  buf.write('0', 156, 1, 'ascii');                                   // typeflag: 普通文件
  buf.write('ustar\0', 257, 6, 'ascii');                             // magic
  buf.write('00', 263, 2, 'ascii');                                  // version
  let sum = 0;
  for (const b of buf) sum += b;
  put(sum.toString(8).padStart(6, '0') + '\0 ', 148, 8);
  return buf;
}

async function tarGz(rootDir, outFile) {
  const files = [];
  const walk = (dir, prefix) => {
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const rel = prefix ? `${prefix}/${e.name}` : e.name;
      if (e.isDirectory()) walk(join(dir, e.name), rel);
      else files.push({ rel, abs: join(dir, e.name), size: statSync(join(dir, e.name)).size });
    }
  };
  walk(rootDir, '');

  const tmpTar = `${outFile}.tar`;
  const ws = createWriteStream(tmpTar);
  const w = (chunk) => new Promise((res, rej) => ws.write(chunk, (e) => (e ? rej(e) : res())));
  for (const f of files) {
    await w(ustarHeader(f.rel, f.size));
    await new Promise((res, rej) => {
      const rs = createReadStream(f.abs);
      rs.on('data', (c) => {
        if (!ws.write(c)) {
          rs.pause();
          ws.once('drain', () => rs.resume());
        }
      });
      rs.on('end', res);
      rs.on('error', rej);
    });
    const pad = (512 - (f.size % 512)) % 512;
    if (pad) await w(Buffer.alloc(pad));
  }
  await w(Buffer.alloc(1024)); // tar 结束块
  await new Promise((res) => {
    ws.end(res);
  });
  await pipeline(createReadStream(tmpTar), createGzip({ level: 1 }), createWriteStream(outFile));
  rmSync(tmpTar, { force: true });
}

log(`打包 -> ${out}`);
await tarGz(root, out);
rmSync(work, { recursive: true, force: true });
log(`完成: ${out}`);
// stdout 只输出一行: 该镜像载(docker load)入后的名称, 便于脚本继续 docker tag
console.log(`${repoName}:${tag}`);
