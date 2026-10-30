# Dragonwell 25 RISC-V Anolis 构建

与 openjdk-build-new 的同名修复分支配套使用。日常引用统一为 master；验证分支时通过
SCM、BUILD_REF、CI_REF 选择修复分支。

## 镜像

RISC-V 任务拉取 `alibabadragonwelljdk/dragonwell25-riscv64-build:anolis23.5-rva23-v1`，
在 x86_64 节点的容器里运行原有 build-farm 入口，不在每个 JDK 构建中重新编译 GCC。
Dockerfile 和可缓存的构建脚本位于构建仓库的 `docker/dragonwell25-riscv64/`。
首次启用前先构建并推送镜像；本仓库配置中的标签是发布约定，不代表已发布。
如使用其他镜像仓库或 digest，修改 `jdk25u_pipeline_config.groovy` 的 `dockerImage`。

工具链和 sysroot 路径由构建仓库的 Linux 平台脚本统一选择，流水线不再传入旧 sysroot。
目标为 Anolis GCC 14.3.0、glibc 2.38，ABI 为 `lp64d`。ISA 使用已验证的
`rv64imafdcv_..._zcmop` 显式扩展列表，完整默认值位于构建仓库的 `linux.sh`。
RISC-V 配置的 `dockerArgs: '-e RISCV_MARCH='` 会清除旧 v1 镜像携带的 ISA 默认值，
由构建脚本选择参数，无需为这次参数调整重新构建或推送镜像。
调试其他 ISA 时，可将 DOCKER_ARGS 设为 `-e RISCV_MARCH=<完整扩展列表>`。

## 临时跳过构建后校验

`pipelines/defaults-build-scripts-new.json` 中的配置：

```json
"postBuildValidation": {
  "skipAll": true
}
```

临时应用于使用这份 defaults 的所有 JDK 版本、平台和发行变体，包括 RELEASE=true 的发布构建。
编译、打包、制品归档和 SBOM 生成保持开启；
跳过 SBOM 校验、SmokeTests、AQA/TCK、可复现比较。
构建描述和日志会标明 BUILD ONLY，不能据此声称通过运行验证。
签名和安装包生成流程保持原有行为。
Dragonwell 25 RISC-V 交叉构建在该策略开启时，从生成 JDK 的 `release` 文件读取版本元数据，
不再调用旧 QEMU 环境执行 `java -version`；元数据会明确标注目标 JVM 未执行。
关闭跳过策略后恢复原有运行版本读取流程。

恢复时将 skipAll 改为 false，或者删除 postBuildValidation 配置。
如需缩小范围，可设置 skipAll=false，并使用 skip 列出具体的
`JDK/VARIANT/OS/ARCH`；这种定向跳过只适用于非发布构建。
公共 SBOM 校验任务的源码检出与制品复制修复另行处理，不包含在本次变更中。

## 启用顺序

1. 构建并发布上述镜像；可发布后使用 registry 返回的 digest 固定引用。
2. 合入构建仓库的 RISC-V 变更，再合入本仓库的 RISC-V 镜像配置。
3. 上游 openjdk25-pipeline 的 SCM 切至 master，DEFAULTS_JSON/DEFAULTS_URL 使用 master 的
   defaults-build-scripts-new.json。若 DEFAULTS_JSON 内嵌完整 JSON，必须同步添加
   postBuildValidation，并把 build_branch、pipeline_branch 和 defaultsUrl 改为 master。
4. 所有需要临时跳过校验的上游任务都需使用上述新流水线代码与 defaults，再重新生成各 JDK 的下游任务。
   已有任务内嵌的 DEFAULTS_JSON 不会随 Git 文件修改自动更新。
5. 确认 jdk25u RISC-V 的 DOCKER_IMAGE 已更新，旧的 sysroot configure 覆盖已清空，
   然后运行 RISC-V 构建。临时跳过策略与 RISC-V 镜像配置分别提交，可独立回退。

## 测试

```bash
cd pipelines
./gradlew test --tests PostBuildValidationPolicyTest --tests IndividualBuildConfigTest --tests VersionInfoTest
```

## 通过现有上游任务调试

在 `build-scripts-new/openjdk25-pipeline` 的单次构建参数中设置：

- `targetConfigurations` 先选 `{"riscv64Linux":["dragonwell"]}`；成功后再选原有全部平台。
- `buildReference` 指定构建脚本分支或 SHA，`ciReference` 指定流水线分支或 SHA。
- `defaultsJson.repository.pipeline_branch` 同样指向被测流水线版本，因为上游会先从这里加载平台配置。
- `defaultsJson.postBuildValidation` 保留 `{"skipAll":true}`，应用当前临时跳过策略。
- `useAdoptBashScripts=false`，`propagateFailures=true`，`releaseType=Nightly Without Publish`。

这些是单次构建覆盖，不需要为每个调试分支修改代码中的默认 master 引用。
Alpine Docker 任务使用 Linux 宿主节点；`TARGET_OS` 仍为 `alpine-linux`。

### Restricting a single verification run to a build node

To avoid a full or unavailable worker during debugging, add an optional restriction to the run's `defaultsJson`:

```json
"buildNodeLabels": {
  "alpine-linux-x64-dragonwell": "worker-with-free-space"
}
```

Keys are `<target-os>-<target-arch>-<variant>`. The expression is appended with `&&` to the existing build, OS, host architecture and Docker requirements; it cannot replace those requirements. Omit it for normal scheduling. This is applied by the upstream pipeline when constructing downstream build parameters and does not change saved job labels.
