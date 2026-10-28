# 交接文档：商品推荐系统

> 给新对话用。读完这份就能立刻接上，不需要回溯聊天记录。
> 最后更新：2026-10-28

---

## 一、这是谁的项目、要干什么

**使用者**：已毕业，软件开发方向。**这不是毕设、不是课程作业**，是纯粹的自学练手项目。

**目标**：从零搭建一个电商平台的个性化推荐系统，重点是把
「数据采集 → 相似度计算 → 召回排序 → 冷启动兜底 → 效果评测」这条链路真正跑通。

**参考项目**：`D:\桌面\大学\mall`（新蜂商城 newbee-mall，Spring Boot 2.3.7 + Thymeleaf + MyBatis，7293 行）。
它自带一套 UserCF 推荐，但实现有致命缺陷——这正是本项目的起点。
（`D:\桌面\大学\综合课设` 是用户最初给错的参考路径，已弃用。）

---

## 二、当前进度

| 阶段 | 状态 |
| --- | --- |
| 参考项目分析 | ✅ 完成（`docs/00`） |
| 可行性分析 / 需求规格 / 概要设计 / 数据库设计 | ✅ 完成（`docs/01/02/03/05`） |
| 开发上手指南（环境+建包+编码步骤+Git规范） | ✅ 完成（`docs/10`） |
| **编码阶段** | 🔄 **未开始——用户正在从零手写** |
| 详细设计说明书（`docs/04`） | ⬜ 待写（建议写代码前先补） |
| 测试计划与报告（`docs/06`） | ⬜ 有代码后再写 |
| 部署手册（`docs/07`）、用户手册（`docs/08`） | ⬜ 能跑起来后再写 |
| 前端 Vue 3（`recommend-web/`） | ⬜ 未开始 |

**关键事实**：`recommend-server/src` **是空的**（工作区 0 个 .java），用户选择自己从零重写，
不要直接帮他把代码写完。

> 存档：完整骨架（89 个 Java 文件）在 tag `skeleton-v1`，
> `git checkout skeleton-v1 -- recommend-server/src` 可取回作参考。

---

## 三、仓库信息

| 项 | 值 |
| --- | --- |
| 本地路径 | `D:\桌面\商品推荐系统` |
| 远端 | `git@github.com:yueseyuyeyu/recommend.git`（**Private**） |
| 分支 | `main`，已与远端同步（HEAD `3dc41d7`） |
| GitHub 用户名 | **`yueseyuyeyu`**（与提交者名 `dengwenpeng` 不同，别混） |
| 提交者 | `dengwenpeng <1464855140@qq.com>` |

---

## 四、本机环境（都已配好，别重复折腾）

| 组件 | 路径/版本 |
| --- | --- |
| JDK 17 | `D:\java17`（17.0.5）。另有 `D:\java8`，**系统 JAVA_HOME 指向 java8，不要改** |
| Maven | 项目用 **Maven Wrapper**（`recommend-server/mvnw`），锁 3.9.16 |
| Maven 本地仓库 | **`D:\maven-repo`**（已从 C 盘迁走，见下） |
| Maven settings | `C:\Users\邓董\.m2\settings.xml`（配了阿里云镜像） |
| MySQL | 8.0.27，`D:\mysql\mysql-8.0.27-winx64` |
| Node | v22 |
| IDE | IntelliJ IDEA 2026.2.3，`D:\IDEA\...` |

**构建命令**（必须带 JAVA_HOME）：
```bash
cd D:/桌面/商品推荐系统/recommend-server
JAVA_HOME=D:/java17 ./mvnw -B -q compile
```

**GitHub SSH**：本机 22 端口被封，走 443。已配 `C:\Users\邓董\.ssh\config`：
```
Host github.com
    HostName ssh.github.com
    Port 443
    User git
    IdentityFile C:/Users/邓董/.ssh/id_ed25519_github
    IdentitiesOnly yes
```
全局已设 `core.sshCommand = C:/Windows/System32/OpenSSH/ssh.exe`
（Git Bash 自带 ssh 因中文用户名路径乱码读不到 config，必须用 Windows 版）。

---

## 五、已完成的工作清单

1. **参考项目深度分析**（`docs/00`）—— 12 个算法缺陷 + 12 项工程债
2. **全套过程文档** —— 可行性 / 需求规格 / 概要设计 / 数据库设计 / 开发上手指南
3. **建表脚本** `sql/schema.sql` —— 8 张表 + 15 个索引（这份是 AI 写的，用户可能想自己重写）
4. **README** —— 以 newbee-mall 的 `Map.values()` 缺陷开场，定位为自学项目
5. **Git 与 GitHub** —— SSH 走 443 打通、仓库已推送、私钥新建并注册
6. **环境迁移** —— Maven 本地仓库从 C 盘（94% 满）迁到 `D:\maven-repo`

---

## 六、关键决策（别推翻，除非用户说）

| 决策 | 理由 |
| --- | --- |
| Spring Boot 3 + JDK 17 + MyBatis-Plus + MySQL 8 | 用户选定 |
| 前端 Vue 3 + Element Plus + Vite | 用户选定 |
| **推荐算法纯 Java 手写，不引入 ML 框架** | 学东西，调 API 学不到 |
| UserCF + ItemCF 双引擎 + 热门兜底 + 混合 | 用户选定 |
| 算法层不碰数据库（输入是 `UserItemMatrix`） | 保证纯函数、可单测 |
| Maven Wrapper 锁 3.9.16 | 换机免配环境 |
| `src` 清空由用户自己重写 | 用户明确要求"辅助我"，不要代劳 |

---

## 七、下一步（用户正在做）

按 `docs/10` 的 10 个阶段推进，**每阶段一个 commit**：

1. `sql/schema.sql`（8 张表）
2. `entity/enums` → `UserRole`、`BehaviorType`、`OrderStatus`
3. `entity`（8 个实体）
4. `common`（Result/ResultCode/BusinessException/GlobalExceptionHandler/PageResult）
5. `config` + `security`（JWT）
6. `mapper` + `application.yml`
7. **`recommend` 包（核心）**：CosineSimilarity → UserItemMatrix → 四个策略 → RecommendEngine
8. 算法单元测试
9. `dto`/`vo` + 各 Service + Controller
10. 离线评测出 Precision/Recall/F1/Coverage

---

## 八、协作方式（重要）

- **不要直接替用户写完代码**。给步骤、给文件清单、给命名规范；他写一段你看一段。
- **下载任何东西前先检索本机**，没有就先问下载路径（之前没查就下了 Maven，其实本机早有 3.6.2）。
- 提交规范：一次改动 = 一个 commit = 补一次测试；**不要用 `git add -A`**，用明确路径。
- `AGENTS.md` 里有完整契约，改代码前先读。

---

## 九、踩过的坑（别重蹈）

1. `CoreMath.recommend()` 用 `distances.values()` 取的是**用户 ID** 不是相似度 → 选出 ID 最大的 3 人
2. 行为强度用 `Math.random()` → 结果不可复现
3. 皮尔森 n=0 返回 NaN，且 `NaN==0` 为 false → 除零判断失效
4. 测试放 `src/main/test/` → Maven 根本不执行
5. 密码无加盐 MD5 → 应用 BCrypt
6. 数据库口令明文写进配置 → 走环境变量 + `.env.example`
7. 我曾把参考项目的真实 MySQL 口令当"反面例子"写进文档并推上去了 →
   已用 `filter-branch` 重写历史清除。**教训：反面例子也不要写真口令**
8. 用户在 GitHub 网页和本地编辑器同时改 README → 冲突，需 rebase

---

## 十、还欠着的

- `docs/04-详细设计说明书`（建议现在就补，是写代码前的施工图）
- `docs/06/07/08`（等有代码/能跑/前端完成）
- 文档转 `.docx`（用户要过 Word 版）
- 旧 Maven 仓库 `C:\Users\邓董\.m2\repository`（174MB）未删，验证稳定后可清理
- 前端 `recommend-web/` 未建
