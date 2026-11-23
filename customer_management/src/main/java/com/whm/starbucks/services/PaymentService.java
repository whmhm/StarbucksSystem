package com.whm.starbucks.services;

import com.whm.starbucks.dao.OrdersRepository;
import com.whm.starbucks.dao.PaymentRecordRepository;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.PaymentDto;
import com.whm.starbucks.pojo.entity.Orders;
import com.whm.starbucks.pojo.entity.PaymentRecord;
import com.whm.starbucks.pojo.enums.OrderStatus;
import com.whm.starbucks.pojo.vo.PaymentVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付服务（Mock 渠道）。
 *
 * ============================ 本类要实现的三件事 ============================
 * 1. pay()    ：把 0 待支付 的订单变成 1 已支付，并留下一条支付流水；
 * 2. refund() ：把已支付的订单变成 6 已退款，并把流水置为已退款；
 * 3. 幂等     ：同一笔支付被重复提交时，只有第一次真正改状态，后面都返回同一结果。
 * ==========================================================================
 *
 * 实现顺序建议：先写 pay() 的「正常路径」跑通，再补幂等分支，最后写 refund()。
 * 每一步的校验顺序在下面的 TODO 里已经排好——顺序不能随便调，理由写在对应步骤里。
 *
 * 事务边界：pay() 里「写流水 + 改订单状态」必须在同一个事务里，
 * 否则会出现「流水记了但订单还是待支付」，或者反过来「订单已支付但没有流水」，对账时无从追查。
 */
@Service
public class PaymentService {

    @Resource
    private PaymentRecordRepository paymentRecordRepository;

    @Resource
    private OrdersRepository ordersRepository;

    /** 时间格式必须与 payment_record / orders 的 DATETIME 列一致；LocalDateTime.toString() 会带 "T"，MySQL 会截断或报错 */
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 流水号里的时间片段，与 OrdersService.generateOrderNo 同风格 */
    private static final DateTimeFormatter TRANSACTION_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 已开通的支付渠道白名单，新增渠道只改这里 */
    private static final Set<String> SUPPORTED_PAY_CHANNELS =
            new HashSet<>(Arrays.asList("MOCK", "WECHAT", "ALIPAY", "BALANCE"));

    /**
     * 发起支付（Mock：不做真实扣款，校验通过即视为支付成功）。
     *
     * 实现说明：下面的步骤顺序本身是设计的一部分，不要随意调整，理由见各步骤。
     *
     * 步骤 1｜入参校验（不碰 DB）
     *   - dto 为 null / orderId 为空或 <= 0 → BizException("支付订单不能为空")
     *   - userId 为 null → BizException(401, "未登录或登录已失效")
     *   - payChannel 归一化：为空取 "MOCK"；非空则转大写并校验是否在白名单
     *     {MOCK, WECHAT, ALIPAY, BALANCE} 内，不在则 BizException("不支持的支付渠道")
     *
     * 步骤 2｜查订单并校验归属（安全边界，必须在幂等之前做）
     *   - ordersRepository.selectOrderById(orderId)，为 null → BizException(404, "订单不存在")
     *   - order.getUserId() 与当前 userId 不一致 → BizException(403, "无权操作该订单")
     *     注意：先查归属再做幂等，否则别人拿你的 orderId + 你的 transactionNo 也能探到你的流水。
     *
     * 步骤 3｜幂等分支 A：按 transactionNo 查是否已有流水
     *   - 若 dto.transactionNo 有值，先 paymentRecordRepository.selectByTransactionNo()；
     *     命中即说明这笔支付已经处理过，直接返回既有结果（组装 PaymentVo，idempotent = true），不要再动任何数据。
     *   - 若 transactionNo 为空，用 generateTransactionNo(channel) 生成一个新的继续往下走。
     *
     * 步骤 4｜幂等分支 B：该订单是否已经支付成功
     *   - paymentRecordRepository.selectPaidByOrderId(orderId) 非空 → 说明已支付过，
     *     直接用这条流水组装 PaymentVo 返回，idempotent = true。
     *   - 这一步防的是「客户端换了个 transactionNo 又点了一次支付」，
     *     只靠步骤 3 拦不住，因为流水号变了但订单还是同一笔。
     *
     * 步骤 5｜状态校验（用状态机，不要写 if status != 0）
     *   - OrderStatus.canTransit(order.getOrderStatus(), OrderStatus.PAID) 为 false
     *     → BizException("当前订单状态不可支付：" + OrderStatus.descOf(order.getOrderStatus()))
     *   - 这一步会顺带拦住「已取消的订单还能付款」「已退款的订单还能付款」。
     *
     * 步骤 6｜写流水（初始状态待支付）
     *   - new PaymentRecord()，字段：orderId、payChannel、payAmount = order.getPayAmount()、
     *     transactionNo、payStatus = 0、isDeleted = 0、gmtCreate / gmtModified = 当前时间字符串
     *   - 【金额只从 order 取】绝不读 dto 里的金额，PaymentDto 也刻意没有这个字段。
     *   - insertPaymentRecord(record)，返回 != 1 → BizException(500, "支付流水创建失败")
     *   - record.getId() == null → BizException(500, "支付流水主键回填失败，检查 keyProperty 配置")
     *   - 并发下两次请求可能同时通过步骤 3，插入时会撞 uk_transaction_no 抛 DuplicateKeyException：
     *     捕获它，回查一次 selectByTransactionNo，按幂等返回，不要把这个异常变成 500。
     *
     * 步骤 7｜流水置为成功（带状态守卫，恰好一次）
     *   - updatePaymentSuccess(record)，返回 != 1 → BizException(409, "支付流水状态已变更，请勿重复支付")
     *
     * 步骤 8｜订单置为已支付（CAS，必须校验影响行数）
     *   - updateOrderStatus(orderId, OrderStatus.UNPAID, OrderStatus.PAID)
     *   - 返回 == 0 → 说明订单状态被并发改掉了（例如超时关单刚好执行），
     *     抛 BizException(409, "订单状态已变更，请刷新后重试")，让事务回滚掉步骤 6/7 的流水。
     *   - 返回 0 时不要「再查一次然后继续」——那会把并发竞态悄悄吞掉。
     *
     * 步骤 9｜组装返回值
     *   - idempotent = false，orderStatus = OrderStatus.PAID
     *
     * 时间格式：与 OrdersService 保持一致 yyyy-MM-dd HH:mm:ss（实体字段是 String，不是 Date）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentVo pay(Long userId, PaymentDto dto) {
        // ---------- 1. 入参边界 ----------
        if (dto == null || dto.getOrderId() == null || dto.getOrderId() <= 0) {
            throw new BizException("支付订单不能为空");
        }
        if (userId == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        String payChannel = normalizePayChannel(dto.getPayChannel());

        // ---------- 2. 订单 + 归属（必须先于幂等，否则会探到别人的流水） ----------
        Orders order = ordersRepository.selectOrderById(dto.getOrderId());
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该订单");
        }

        // ---------- 3. 幂等 A：同一个 transactionNo 只处理一次 ----------
        if (StringUtils.hasText(dto.getTransactionNo())) {
            PaymentRecord existing = paymentRecordRepository.selectByTransactionNo(dto.getTransactionNo());
            if (existing != null) {
                // 流水号是全局唯一的：不校验归属就返回，等于把别人的流水回给了调用方
                if (!existing.getOrderId().equals(dto.getOrderId())) {
                    throw new BizException("支付流水与订单不匹配");
                }
                return toPaymentVo(existing, order, true);
            }
        }

        // ---------- 4. 幂等 B：该订单已支付过（客户端换了流水号重试） ----------
        PaymentRecord paid = paymentRecordRepository.selectPaidByOrderId(dto.getOrderId());
        if (paid != null) {
            return toPaymentVo(paid, order, true);
        }

        // ---------- 5. 状态机校验 ----------
        if (!OrderStatus.canTransit(order.getOrderStatus(), OrderStatus.PAID)) {
            throw new BizException("当前订单状态不可支付：" + OrderStatus.descOf(order.getOrderStatus()));
        }

        // ---------- 6. 写流水 ----------
        // 客户端传了就必须用它：重试时要复用同一个号，
        // 否则每次重试都生成新流水号，「按流水号幂等」永远不命中
        String transactionNo = StringUtils.hasText(dto.getTransactionNo())
                ? dto.getTransactionNo()
                : generateTransactionNo(payChannel);
        String now = LocalDateTime.now().format(DATE_TIME);

        PaymentRecord newRecord = new PaymentRecord();
        newRecord.setOrderId(order.getId());
        newRecord.setPayChannel(payChannel);
        newRecord.setPayAmount(order.getPayAmount());
        newRecord.setTransactionNo(transactionNo);
        newRecord.setPayStatus(0);
        newRecord.setIsDeleted(0);
        newRecord.setGmtCreate(now);
        newRecord.setGmtModified(now);

        int result = paymentRecordRepository.insertPaymentRecord(newRecord);
        if (result != 1) {
            throw new BizException(500, "支付流水创建失败");
        }
        // 主键回填校验要看【刚插入的对象】。
        // 原实现另查一次 selectPaidByOrderId，但这条流水此时 pay_status 还是 0，必然查到 null，info.getId() 直接 NPE。
        if (newRecord.getId() == null) {
            throw new BizException(500, "支付流水主键回填失败，检查 keyProperty 配置");
        }

        // ---------- 7. 流水置为成功 ----------
        int res = paymentRecordRepository.updatePaymentSuccess(newRecord);
        if (res != 1) {
            throw new BizException(409, "支付流水状态已变更，请勿重复支付");
        }
        // updatePaymentSuccess 只改库不改内存对象，这里同步一下，否则返回给前端的是 0 待支付
        newRecord.setPayStatus(1);

        // ---------- 8. 订单置为已支付（CAS） ----------
        int ordStatus = ordersRepository.updateOrderStatus(order.getId(), OrderStatus.UNPAID, OrderStatus.PAID);
        if (ordStatus != 1) {
            throw new BizException(409, "订单状态已变更，请刷新后重试");
        }
        order.setOrderStatus(OrderStatus.PAID);

        // ---------- 9. 组装返回值 ----------
        return toPaymentVo(newRecord, order, false);
    }

    /**
     * 支付渠道归一化：空值按 MOCK 处理，非空统一大写后校验白名单。
     * 不做大写归一的话，前端传 "mock" 会被判成不支持的渠道。
     */
    private String normalizePayChannel(String payChannel) {
        if (!StringUtils.hasText(payChannel)) {
            return "MOCK";
        }
        String normalized = payChannel.trim().toUpperCase();
        if (!SUPPORTED_PAY_CHANNELS.contains(normalized)) {
            throw new BizException("不支持的支付渠道");
        }
        return normalized;
    }

    /**
     * 流水 → PaymentVo 的统一映射（消除 IDE 报的 duplicated code fragment）。
     *
     * 三处各拼一遍的隐患不只是重复：任何一处少填字段，
     * 都会让「首次支付」与「幂等返回」的响应结构不一致，
     * 前端在 idempotent=true 时会莫名少几个字段，排查成本很高。
     *
     * @param order      关联订单，用于补 orderNo 与最新订单状态；可为 null
     * @param idempotent 本次是否命中幂等
     */
    private PaymentVo toPaymentVo(PaymentRecord record, Orders order, boolean idempotent) {
        PaymentVo vo = new PaymentVo();
        vo.setPaymentId(record.getId());
        vo.setOrderId(record.getOrderId());
        vo.setPayChannel(record.getPayChannel());
        vo.setPayAmount(record.getPayAmount());
        vo.setTransactionNo(record.getTransactionNo());
        vo.setPayStatus(record.getPayStatus());
        if (order != null) {
            vo.setOrderNo(order.getOrderNo());
            vo.setOrderStatus(order.getOrderStatus());
        }
        vo.setIdempotent(idempotent);
        return vo;
    }

    /**
     * 退款（Mock：只改状态，不涉及真实资金）。
     *
     * 实现说明（步骤顺序是设计的一部分）：
     *   1. 查订单 + 归属校验（同 pay 的步骤 1、2）；
     *   2. 查成功流水：paymentRecordRepository.selectPaidByOrderId(orderId)，
     *      为 null → BizException("该订单没有可退款的支付记录")；
     *   3. 状态校验：OrderStatus.canTransit(order.getOrderStatus(), OrderStatus.REFUNDED)，
     *      不通过则提示「当前状态不可退款」；
     *   4. 先改订单：updateOrderStatus(orderId, 当前状态, OrderStatus.REFUNDED)，
     *      返回 0 → BizException(409, "订单状态已变更，请刷新后重试")；
     *   5. 再改流水：updatePaymentRefunded(record)，返回 0 → BizException(500, "退款流水更新失败")；
     *   6. 返回 PaymentVo（payStatus = 3，orderStatus = 6）。
     *
     * 为什么「先订单后流水」：订单状态是主状态，先落主状态，
     * 万一流水更新失败，事务会整体回滚，不会出现「流水已退、订单仍已支付」的中间态被提交。
     *
     * 生产环境注意：真实退款要调用渠道退款接口，顺序应改为
     * 「先落退款申请流水 → 调渠道 → 渠道成功回调后再改订单状态」，
     * 因为渠道调用不可回滚，不能包在数据库事务里想当然。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentVo refund(Long userId, Long orderId) {
//        入参边界
        if (userId == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        if (orderId == null || orderId <= 0) {
            throw new BizException(400, "订单编号不能为空");
        }
        Orders order = ordersRepository.selectOrderById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BizException(403, "无权操作该订单");
        }
        // 已退款的订单先拦掉：selectPaidByOrderId 只查 pay_status = 1，
        // 退款成功后流水变成 3 就查不到了，继续往下走会误报「没有可退款的支付记录」(404)，
        // 让人以为退款记录丢了。这里先挡住，给出准确语义。
        if (order.getOrderStatus() == OrderStatus.REFUNDED) {
            throw new BizException(409, "该订单已退款，请勿重复操作");
        }
        // 查支付流水
        PaymentRecord record = paymentRecordRepository.selectPaidByOrderId(orderId);
        if( record == null ){
            throw new BizException(404, "该订单没有可退款的支付记录");
        }
        if (!OrderStatus.canTransit(order.getOrderStatus(), OrderStatus.REFUNDED)) {
            throw new BizException("当前订单状态不可退款：" + OrderStatus.descOf(order.getOrderStatus()));
        }
        int ordStatus = ordersRepository.updateOrderStatus(orderId, order.getOrderStatus(), OrderStatus.REFUNDED);
        if (ordStatus != 1) {
            throw new BizException(409, "订单状态已变更，请刷新后重试");
        }
        int result = paymentRecordRepository.updatePaymentRefunded(record);
        if (result != 1) {
            throw new BizException(500, "退款流水更新失败");
        }
        // 两个 update 都只改库、不改内存对象；不同步的话返回给前端的是「已支付/待接单」，
        // 前端弹「退款成功」但状态还是旧值
        order.setOrderStatus(OrderStatus.REFUNDED);
        record.setPayStatus(3);
        return toPaymentVo(record, order, false);
    }

    /**
     * 生成 Mock 流水号。
     *
     * TODO(你来实现)：格式建议 MOCK + yyyyMMddHHmmssSSS + 3 位随机数（与 generateOrderNo 同风格），
     * 长度必须 <= 64（payment_record.transaction_no 列宽）。
     * 不要让客户端能猜到别人的流水号——虽然它不是权限凭证，但能被用来试探幂等分支。
     */
    private String generateTransactionNo(String payChannel) {
        // 随机段补足 3 位，保证流水号长度稳定（列宽 VARCHAR(64)），也避免 "MOCK...12" 与 "...012" 视觉混淆
        return payChannel + LocalDateTime.now().format(TRANSACTION_NO_TIME)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
