package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 支付流水数据访问层。
 *
 * 配套：sql/order_closure_migration.sql 中的 payment_record 表。
 * 约定：所有方法参数都用 @Param，XML 里统一写 #{record.xxx} / #{orderId}，
 * 不要依赖单参数省略 @Param 的隐式绑定（项目里已有 Mapper 因这个踩过坑）。
 */
@Repository
public interface PaymentRecordRepository {

    /**
     * 按支付流水号查询（含已删除？不含——is_deleted = 0）。
     *
     * 用途：支付幂等的第一道校验。客户端/渠道可能重复提交同一个 transactionNo，
     * 命中即视为「已经处理过」，直接返回既有流水，不要再插一条。
     */
    PaymentRecord selectByTransactionNo(@Param("transactionNo") String transactionNo);

    /**
     * 查询某笔订单「支付成功」的流水（pay_status = 1 且 is_deleted = 0）。
     *
     * 用途：1) 防止同一订单重复支付；2) 订单详情页回显支付渠道与流水号。
     * 返回 null 表示该订单尚未支付成功。
     */
    PaymentRecord selectPaidByOrderId(@Param("orderId") Long orderId);

    /**
     * 插入一条支付流水。
     *
     * XML 必须配 useGeneratedKeys="true" keyProperty="record.id"：
     * 支付成功时要拿流水主键做后续关联，回填失败说明配置写错，Service 需显式校验。
     *
     * @return 受影响行数，正常为 1；并发撞 uk_transaction_no 时抛 DuplicateKeyException
     */
    int insertPaymentRecord(@Param("record") PaymentRecord record);

    /**
     * 把流水置为「支付成功」：pay_status 0/2 → 1。
     *
     * XML 的 WHERE 必须带 pay_status IN (0, 2) 条件（乐观锁式的状态守卫），
     * 这样重复回调时第二次影响行数为 0，Service 据此判断「已经处理过」而不是覆盖。
     *
     * @return 受影响行数，1 = 本次真正完成了状态跃迁，0 = 已被处理或不存在
     */
    int updatePaymentSuccess(@Param("record") PaymentRecord record);

    /**
     * 把流水置为「已退款」：pay_status 1 → 3。
     *
     * 同样用 WHERE pay_status = 1 守卫，避免对未支付/已退款的流水重复退款。
     *
     * @return 受影响行数，1 = 退款成功，0 = 状态不允许
     */
    int updatePaymentRefunded(@Param("record") PaymentRecord record);
}
