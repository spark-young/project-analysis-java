# 项目交接 Skill 评估与选用参考

> 目的：为"项目执行到某个阶段要交给另一个团队/Agent 继续实施"选一个交接 Skill。
> 结论：已自建 `project-handover`（基于本项目交接文档结构 + 融合各成熟方案最佳实践）。以下 GitHub 成熟方案供备选/互补。

## 快速对比

| 方案 | 仓库 | 定位 | 最强亮点 | 适用 |
|---|---|---|---|---|
| **CoachSteff/handover-skills** | github.com/CoachSteff/handover-skills | Handover + Resume 双技能 | ✅ **verified-vs-assumed 分离 + source-of-truth-rank**（最强防幻觉） | 长会话/里程碑交接 |
| **Rivercoco/agent-cross-handoff-skill** | github.com/Rivercoco/agent-cross-handoff-skill | 跨 Agent/跨电脑交接（中文） | ✅ 跨机器/跨系统/跨 Agent；敏感数据不进仓库 | 换电脑/换 Agent |
| **wecansync/agent-skills** (agent-handoff) | github.com/wecansync/agent-skills | `.ai/` 目录多 Agent 共享 | ✅ LOG / HANDOFF / decisions 全程留痕；自动注入启动指引 | 长期多 Agent 协作 |
| **smithlamar/handover** | github.com/smithlamar/handover | Claude Code 会话交接 | ✅ 自包含 brief + 一行续跑命令；handoff/pickup/cleanup 三阶段 | 单会话收尾/续接 |

## 各方案关键设计（值得借鉴的点）

### 1. CoachSteff/handover-skills（推荐吸收）
- 产物：`PROGRESS.md`（每次里程碑追加、不清空）+ `HANDOVER.md`（最新 self-contained brief，覆写）+ `handover/`（时间戳快照）+ 可选 `init.sh`。
- **XML 标签化章节**：`<mission>/<current_state>/<artifacts>/<decisions>/<constraints>/<verification>/<next_steps>/<unknowns_and_do_not_assume>/<source_of_truth_rank>/<startup_protocol>`。
- 核心反幻觉机制：**已验证 vs 假设分离**——未验证的进 `<unknowns>` 永不进 `<current_state>`；**真相优先级** `运行代码 > 测试 > 文档 > PROGRESS > HANDOVER`。

### 2. Rivercoco/agent-cross-handoff-skill（中文，契合换团队）
- 核心原则：**代码走 Git、项目规则写进 `AGENTS.md`、Claude 入口写进 `CLAUDE.md`、交接状态写进 `docs/AI_AGENT_HANDOFF.md`、敏感数据永远不进仓库**。
- 提供 install.sh / install.ps1，覆盖 Codex/Claude Code 与多系统迁移。

### 3. wecansync/agent-skills（agent-handoff）
- 安装到 `.ai/`：`PROJECT.md / PATHS.md / PLAN.md / conversations/{HANDOFF.md, LOG.md, decisions/, sessions/YYYY-MM-DD/}`。
- 自动向代理配置注入「开工先读、收工追加 LOG、变更更新 HANDOFF」的常驻片段。
- 安装脚本可安全重跑，不覆盖已有项目指令。

### 4. smithlamar/handover
- 三指令：`/handover <下一阶段目标>`（写 brief + 给一行续跑命令）、`/handover <file>`（pickup 续跑）、`/handover cleanup`（闭环后清理）。
- `disable-model-invocation: true`：防 agent 自动乱触发。
- 核心理念：交接 = **持久化、精选的压缩**（durable, curated compaction），而不是丢人给 lossy summary。

## 与本项目自建 skill 的关系

- 本项目 `project-handover` 已吸收：固定章节结构、约束/勿动清单、后续步骤、真相优先级、隐私边界。
- **可直接升级增强的点**（如需要）：
  1. 把 `<verified>/<unknowns>/<source_of_truth_rank>` 三块做进模板（已含于自建 skill 模板）。
  2. 采用 `.ai/PROJECT.md` 常驻多 Agent 留痕（配 wecansync）。
  3. 交接产物命名/存档策略（配 smithlamar 的 timestamp 快照 + cleanup）。
- **事件驱动的自动化交接**（若想定时/阶段触发）：可用 TRAE 的 Schedule 能力，在里程碑后定时触发一次交接文档生成。

## 建议

- **日常/单团队换手**：直接用自建 `project-handover` 即可，够用且贴合本项目结构。
- **需要跨电脑/跨系统/跨 Agent 协同**：叠加 Rivercoco 的 agent-cross-handoff。
- **长期多 Agent 留痕**：叠加 wecansync 的 agent-handoff（`.ai/` 目录常驻）。
- **追求极致防幻觉**：参考 CoachSteff 的 XML 章节与真相优先级（已融入自建模板）。