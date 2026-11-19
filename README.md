# StarbucksSystem

基于 Spring Boot 的星巴克点单系统，包含以下模块：

- `customer_management`：顾客端（用户、菜单、购物车、订单、门店）
- `store_management`：门店端
- `operations_management`：运营端

## 本地启动

1. 复制配置模板并填入本机数据库账号密码：

   ```bash
   cp customer_management/src/main/resources/application.yml.example \
      customer_management/src/main/resources/application.yml
   ```

2. 在 IDEA 中运行各模块的 `StarbucksApplication`。

> `application.yml` 含有本地数据库密码与 JWT 密钥，已在 `.gitignore` 中排除，请勿提交。
