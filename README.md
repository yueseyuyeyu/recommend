# 商品推荐系统

> 一个**从零学习并搭建**的电商平台个性化推荐系统。
> 不是课程作业，不是毕设——是一个已经毕业的人，想真正搞懂「推荐系统」是怎么从数据一直落到线上的。

---

## 我为什么做这个

网上能找到的 Java 电商项目（比如著名的 [newbee-mall](https://github.com/newbee-ltd/newbee-mall)）
大多止步于 CRUD：商品、购物车、订单、后台管理。少数带了"推荐"功能的，
实现往往经不起推敲。我拿 newbee-mall 当起点读了一遍，发现它的推荐算法有三个致命问题：

```java
// ltd/newbee/mall/core/CoreMath.java —— 所谓的"取前三个相似用户"
Map<Double, Integer> distances = computeNearestNeighbor(userId, list);
// distances 结构：key = 相关系数，value = 用户ID
List<Integer> values = new ArrayList<>(distances.values());  // ← 拿到的是用户ID
values.sort(Collections.reverseOrder());                     // ← 按用户ID倒序排
similarUserIdList = values.stream().limit(3).collect(toList()); // ← 取ID最大的3个用户
```

`Map.values()` 取到的是**用户 ID** 而不是相似度，所以这段代码选出的是
**ID 数值最大的三个人**，跟"兴趣相似"毫无关系。注释写着"找出前三个相似的用户"，
代码做的是另一件事。

再加上行为强度用 `Math.random()` 伪造、皮尔森系数在 `n=0` 时返回 `NaN`、
结果按商品 ID 排序而非推荐分——这套算法实际上是失效的。

**所以这个项目从一开始就是冲着这些问题去的**：不是为了再写一个商城，
而是为了把「数据采集 → 相似度计算 → 召回排序 → 冷启动兜底 → 效果评测」这条链路真正跑通。

> 完整的问题清单见 [docs/00-参考项目分析.md](docs/00-参考项目分析.md)（12 个算法缺陷 + 12 项工程债）。

---

## 我想通过它学到什么

- [ ] 协同过滤到底怎么算——UserCF 与 ItemCF 的区别、各自的适用场景
- [ ] 为什么要用余弦相似度而不是皮尔森，以及热门惩罚因子在抑制什么
- [ ] 冷启动怎么处理才不是"随便返回几个热门"
- [ ] 推荐结果怎么评测——准确率、召回率、覆盖率这些数字是怎么算出来的
- [ ] 怎么把算法设计成**可单元测试**的纯函数（这一点参考项目完全没做到）
- [ ] Spring Boot 3 分层架构、MyBatis-Plus、JWT 鉴权的工程实践
- [ ] 前后端分离怎么组织，接口契约怎么定

---

## 技术选型

| 层次 | 选型 | 为什么 |
| --- | --- | --- |
| 后端 | Spring Boot 3.2 + JDK 17 | 主流 LTS，生态完整 |
| 持久层 | MyBatis-Plus + HikariCP | 单表 CRUD 零 SQL，连接池性能最好 |
| 数据库 | MySQL 8 | 本机已有，索引设计是重点练习对象 |
| 前端 | Vue 3 + Element Plus + Vite | 前后端分离，组件库成熟 |
| 构建 | Maven Wrapper（锁 3.9.16） | clone 下来不用装 Maven 就能构建 |
| 测试 | JUnit 5 + Mockito | 算法层必须能脱离数据库单测 |

**算法不引入任何机器学习框架**——UserCF / ItemCF 全部用纯 Java 手写。
理由是：这两个算法本来就不复杂，手写一遍才能真正理解每一行在做什么；
用 `surprise` 或 `scikit-learn` 调个 API，学不到东西。

---

## 推荐引擎设计

```
RecommendStrategy (策略接口)
 ├── UserCFStrategy      基于用户的协同过滤 → 首页「猜你喜欢」
 ├── ItemCFStrategy      基于物品的协同过滤 → 详情页「相似商品」
 ├── HotFallbackStrategy 热门兜底          → 冷启动 / 降级
 └── HybridStrategy      混合推荐          → 归一化后加权融合
              ↓
        RecommendEngine（门面：策略路由 + 结果补全 + 异常降级）
              ↓
    CosineSimilarity / UserItemMatrix / RecommendEvaluator
```

两个关键设计：

1. **算法层不碰数据库**。输入是 `UserItemMatrix` 内存矩阵，不是 `Connection`。
   所以算法是纯函数，给定相同输入必定返回相同结果，**可以直接单元测试**。
   （参考项目的 `CoreMath` 因为耦合了数据库，压根没法测。）

2. **推荐永远不拖垮页面**。任何算法异常都被 `RecommendEngine` 捕获并降级为热门推荐，
   前端永远能拿到满额结果，不会出现空推荐位。

行为权重设计（显式程度越高、越接近转化，权重越大）：

| 行为 | 权重 |
| --- | --- |
| 浏览 VIEW | 1.0 |
| 点击 CLICK | 2.0 |
| 加入购物车 CART | 3.0 |
| 评分 RATE | 4.0 |
| 购买 PURCHASE | 5.0 |

---

## 当前进度

| 阶段 | 状态 |
| --- | --- |
| 参考项目分析与问题定位 | ✅ 完成 |
| 需求分析 / 概要设计 / 数据库设计 | ✅ 完成 |
| 数据库表结构（8 张表 + 15 个索引） | ✅ 完成 |
| 工程骨架与构建环境（JDK 17 + Maven Wrapper） | ✅ 完成 |
| 后端编码（entity → mapper → 推荐引擎 → service → controller） | 🔄 进行中 |
| 算法单元测试 + 离线评测 | ⬜ 待开始 |
| 前端 Vue 3 界面 | ⬜ 待开始 |

> 代码正在按 [10 个阶段](docs/10-开发上手指南.md#三编码步骤共-10-个阶段每阶段一个-git-commit)逐步重写，
> 每个阶段一个 commit。`src` 目前是空的，骨架存档在 tag `skeleton-v1`。

---

## 快速开始

### 前置要求

- JDK 17（本机 `D:\java17`）
- MySQL 8.0+
- Node.js 18+（前端阶段才需要）

### 三步跑起来

```bash
# 1. 初始化数据库
mysql -u root -p < sql/schema.sql

# 2. 配置环境变量（不要直接改 application.yml）
cp .env.example .env
# 编辑 .env 填入 DB_PASSWORD 和 JWT_SECRET

# 3. 启动后端
cd recommend-server
./mvnw spring-boot:run
```

启动后打开 <http://localhost:8080/swagger-ui.html> 查看接口文档。

> 详细的环境配置（IDEA 怎么配 JDK、Maven、UTF-8、Lombok）见
> [docs/10-开发上手指南.md](docs/10-开发上手指南.md)。

---

## 目录结构

```
商品推荐系统/
├── AGENTS.md                 提交与测试的协作契约
├── .env.example              环境变量模板（真实配置不入库）
├── docs/
│   ├── 00-参考项目分析.md      newbee-mall 深度剖析：算法缺陷 + 技术债
│   ├── 01-可行性分析报告.md
│   ├── 02-需求规格说明书.md     用例模型、功能/非功能需求
│   ├── 03-概要设计说明书.md     架构、技术选型、推荐引擎设计
│   ├── 05-数据库设计说明书.md    表结构、索引、约束
│   └── 10-开发上手指南.md       环境配置 + 建包顺序 + 编码步骤 + Git 规范
├── sql/
│   └── schema.sql            8 张表建表脚本
└── recommend-server/         后端服务
    ├── pom.xml
    ├── mvnw / mvnw.cmd       Maven Wrapper（锁 3.9.16）
    └── src/
```

---

## 开发约定

这个仓库是**给自己看的练习场**，但我仍然按工程规范来约束自己：

- 一次改动 = 一个 commit，message 遵循 Conventional Commits
- 一次改动 = 补一次测试，交付前全量通过
- 严格分层：Controller 不直接调 Mapper，业务逻辑不写进 Controller
- SQL 一律参数化，禁止字符串拼接
- 密码 BCrypt 加盐，配置走环境变量，**任何口令不进版本库**

详见 [AGENTS.md](AGENTS.md)。

---

## 参考与致谢

- [newbee-mall](https://github.com/newbee-ltd/newbee-mall) —— 起点和分析对象
- 《推荐系统实践》项亮 —— 协同过滤部分的理论依据
