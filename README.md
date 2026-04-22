# 商品个性化推荐系统 Java + Vue 改造版

当前分支把原 Flask 单体项目拆成三部分：

- `backend/`: Spring Boot 3 + MyBatis-Plus，负责主业务接口、推荐、分析、后台管理。
- `frontend/`: Vue 3 + Vite + Element Plus，复用原系统的侧边栏、卡片、商品网格和登录页样式。
- `sentiment-service/`: FastAPI，只保留评论情感分析、批量情感分析、关键词提取。

## 启动 MySQL

先执行数据库脚本：

```sql
source database/schema.sql;
```

默认连接配置在 `backend/src/main/resources/application.yml`：

```yaml
spring.datasource.url: jdbc:mysql://localhost:3306/phone_recommend
spring.datasource.username: root
spring.datasource.password: root
```

如本地密码不同，直接改这个文件。

## 启动 Python 情感服务

```powershell
cd sentiment-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn main:app --reload --port 8000
```

健康检查：

```text
http://localhost:8000/api/health
```

## 启动 Java 后端

需要本机安装 Maven 和 Java 17。

```powershell
cd backend
mvn spring-boot:run
```

健康检查：

```text
http://localhost:8080/api/health
```

## 启动 Vue 前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

访问：

```text
http://localhost:5173
```

默认账号：

- 用户：`Test` / `123456`
- 管理员：`admin` / `admin123`

## 当前迁移边界

Python 只负责：

- `POST /api/sentiment/analyze`
- `POST /api/sentiment/batch`
- `POST /api/sentiment/keywords`

Java 负责：

- 登录和 JWT
- 商品、评论、用户 CRUD
- 浏览行为记录
- 个性化推荐、冷启动推荐、相似商品
- 数据大屏和关联分析接口

Vue 负责：

- 前台页面
- 后台管理页面
- ECharts 数据展示

## 后续要补的数据迁移

原项目的 `phone_recommend.db` 还在 `main` 分支里。下一步可以做一个迁移脚本，把 SQLite 数据导入 MySQL；也可以用原始 `数据集/phones.csv` 和 `数据集/comments.csv` 重新导入。
