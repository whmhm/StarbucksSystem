-- ============================================================================
-- 订单闭环增量迁移脚本
-- 适用库：starbucks（MySQL 8.0）
-- 执行方式（本机无 mysql CLI 时，用已有镜像连宿主机 3306；密码请用本地环境变量或直接输入，禁止写进仓库）：
--   docker run --rm -i mysql:8.0 mysql -hhost.docker.internal -P3306 \
--     -uroot -p starbucks < sql/order_closure_migration.sql
--
-- 设计原则：
--   1. CREATE TABLE / CREATE INDEX 均带 IF NOT EXISTS，可重复执行；
--   2. ALTER 语句默认注释，执行前先 SHOW INDEX FROM 表名 确认不存在，避免报重复键；
--   3. 不改动已有列，只新增支付表与索引，保证老数据不受影响。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 支付流水表 payment_record
-- ---------------------------------------------------------------------------
-- 为什么需要独立流水表而不是只改 orders.order_status：
--   a. 一次下单可能多次尝试支付（失败重试、换渠道），一次支付也可能对应多次退款，
--      订单状态只有一个字段装不下这段历史；
--   b. transaction_no 是渠道侧流水号，天然唯一，是「幂等」的落点——
--      重复回调/重复点击只认第一次；
--   c. 对账时按流水表统计，不会因为订单状态被后续流转覆盖而丢账。
--
-- 字段与 PaymentRecord 实体一一对应，新增字段必须同步改实体，否则 resultType 映射不到。
CREATE TABLE IF NOT EXISTS `payment_record` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`       BIGINT UNSIGNED NOT NULL                COMMENT '订单主键，关联 orders.id',
  `pay_channel`    VARCHAR(20)     NOT NULL                COMMENT '支付渠道：MOCK-模拟支付 WECHAT-微信 ALIPAY-支付宝 BALANCE-星礼卡余额',
  `pay_amount`     DECIMAL(10,2)   NOT NULL                COMMENT '本次支付金额，必须等于 orders.pay_amount',
  `transaction_no` VARCHAR(64)     NOT NULL                COMMENT '支付流水号，渠道侧唯一，Mock 支付由服务端生成',
  `pay_status`     TINYINT         NOT NULL DEFAULT 0      COMMENT '支付状态：0-待支付 1-支付成功 2-支付失败 3-已退款',
  `is_deleted`     TINYINT         NOT NULL DEFAULT 0      COMMENT '是否删除：0-否 1-是',
  `gmt_create`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后变更时间',
  PRIMARY KEY (`id`),
  -- 幂等的物理保障：并发下即使 Service 判断都放行，也只有一条能插进去
  UNIQUE KEY `uk_transaction_no` (`transaction_no`),
  -- 按订单查流水（订单详情页展示支付信息）
  KEY `idx_order_id` (`order_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '支付流水表';


-- ---------------------------------------------------------------------------
-- 2. orders 索引
-- ---------------------------------------------------------------------------
-- idx_user_id：把「按手机号查订单」改成「按 user_id 查订单」后必须走索引。
--   原实现 selectOrderByUserPhone 用 user_phone 查询且不校验归属，
--   任何登录用户拿到别人手机号就能查别人的订单，闭环时改为按 JWT 里的 userId 查。
-- 执行前先确认： SHOW INDEX FROM orders;
-- ALTER TABLE `orders` ADD INDEX `idx_user_id` (`user_id`);

-- uk_order_no：order_no 是业务唯一号，靠 generateOrderNo() 的「时间戳+3位随机」保证，
--   并发下仍有碰撞概率（同一毫秒 + 同一随机数），没有唯一索引就查不出来。
-- ALTER TABLE `orders` ADD UNIQUE INDEX `uk_order_no` (`order_no`);

-- idx_store_status：门店端按「门店 + 订单状态」拉待接单/制作中列表，这是门店端的主查询。
-- ALTER TABLE `orders` ADD INDEX `idx_store_status` (`store_id`, `order_status`);


-- ---------------------------------------------------------------------------
-- 3. 取餐码唯一性说明（按需启用）
-- ---------------------------------------------------------------------------
-- 星巴克真实的取餐码是「同门店 + 同营业日」唯一，不是全局唯一；
-- 当前 generatePickupCode() 是 6 位纯随机，撞码概率随单量上升，且没有唯一约束兜底。
--
-- 严格做法：增加营业日列后建组合唯一索引（跨天自动可复用，无需清理）：
-- ALTER TABLE `orders`
--   ADD COLUMN `biz_date` CHAR(8) NOT NULL DEFAULT '' COMMENT '营业日 yyyyMMdd，取餐码作用域';
-- ALTER TABLE `orders`
--   ADD UNIQUE INDEX `uk_store_date_pickup` (`store_id`, `biz_date`, `pickup_code`);
--
-- 不引入 biz_date 的简化做法（有副作用，仅 MVP）：
--   建 UNIQUE(store_id, pickup_code)，撞码时插入失败 → Service 捕获 DuplicateKeyException
--   后重新生成取餐码重试（最多 3 次）。副作用是历史订单的取餐码永久占用号码段。


-- ---------------------------------------------------------------------------
-- 4. order_item 索引
-- ---------------------------------------------------------------------------
-- 订单详情要按 order_id 捞明细；表里目前只有主键，订单量上来后是全表扫。
-- ALTER TABLE `order_item` ADD INDEX `idx_order_id` (`order_id`);


-- ---------------------------------------------------------------------------
-- 5. 迁移后自检（期望：payment_record 存在且列数 = 9）
-- ---------------------------------------------------------------------------
SELECT
  TABLE_NAME,
  COLUMN_NAME,
  COLUMN_TYPE,
  IS_NULLABLE,
  COLUMN_DEFAULT,
  COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'payment_record'
ORDER BY ORDINAL_POSITION;
