# AGENTS.md

本文件是本仓库的协作契约。任何对本仓库的改动（无论是人工还是 AI 辅助）都必须遵守以下规则。

## 注意事项

- 每次改动完成后，都必须创建一个对应的 Git commit，以便后续追踪和回滚。
- 每次改动后，都必须编写或更新相关测试，并在交付给用户前，确保所有测试和验证全部通过。

## 1. 提交规范

### 1.1 提交粒度

- 一个逻辑改动 = 一个 commit。禁止把多个不相关的改动塞进同一个 commit。
- 禁止一次性提交整个项目而不拆分。按阶段推进，每个阶段至少一个 commit。
- 每完成一个可验证的里程碑（编译通过 / 测试通过 / 文档定稿）即可提交。

### 1.2 Commit Message 规范

采用 Conventional Commits 格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

`type` 取值：

| type     | 含义                         |
| -------- | ---------------------------- |
| feat     | 新功能                       |
| fix      | 修复缺陷                     |
| docs     | 仅文档变更                   |
| refactor | 重构（不改变外部行为）       |
| test     | 新增或修改测试               |
| chore    | 构建、依赖、配置等非业务改动 |
| perf     | 性能优化                     |
| style    | 代码格式（不影响语义）       |

示例：

```
feat(recsys): implement ItemCF similarity computation

- add cosine similarity over item-user inverted index
- add top-K neighbor truncation
- cover cold-start items with popularity fallback

Refs: #12
```

### 1.3 提交前自检

每次 commit 前必须确认：

- [ ] 代码可以编译（`./mvnw -q compile`）
- [ ] 全部测试通过（`./mvnw test`）
- [ ] 没有把密钥、数据库口令、`.idea/`、`target/` 等文件提交进仓库
- [ ] 新增/修改的代码有对应的测试覆盖

## 2. 测试规范

- 算法核心逻辑（相似度计算、召回、排序、兜底）必须有单元测试。
- Service 层必须有单元测试，数据库交互使用 H2 内存库或 Mockito 隔离。
- 测试命名：`被测方法_场景_预期结果`，例如 `computeSimilarity_noCommonUsers_returnsZero`。
- 不允许出现被注释掉的测试。
- 交付前必须执行全量测试并保证通过，禁止带失败的测试提交。

## 3. 分支策略

- `main`：稳定分支，每个 commit 都应可编译、可测试通过。
- `feat/<模块名>`：功能开发分支，完成后合并进 `main`。
- `fix/<问题描述>`：缺陷修复分支。

## 4. 代码规范

- 包名全小写：`com.deng.recommend.xxx`（禁止出现 `Dao` 这类大写包名）。
- 分层强制：`controller` → `service` → `mapper`，禁止跨层调用，禁止在 Controller 里写业务逻辑。
- 禁止 SQL 字符串拼接，一律使用参数化查询 `#{}`。
- 禁止在代码中硬编码数据库地址、账号、口令，一律走 `application.yml` + 环境变量。
- 密码一律加密存储（BCrypt），禁止明文。
- 禁止 `e.printStackTrace()`，统一使用 Slf4j 日志 + 全局异常处理器。
- 禁止提交注释掉的大段代码（僵尸代码），需要历史请查 Git。

## 5. 文档同步

- 接口变更必须同步更新 `docs/` 下对应文档与 README。
- 数据库表结构变更必须同步更新 `sql/schema.sql` 与数据库设计说明书。
- 新增环境依赖必须同步更新部署手册。
