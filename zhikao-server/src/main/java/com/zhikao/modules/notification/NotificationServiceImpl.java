package com.zhikao.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.cache.CacheService;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.entity.SysNotification;
import com.zhikao.mapper.SysNotificationMapper;
import com.zhikao.modules.notification.vo.NotificationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Notification intents commit with their business transaction; delivery retries from the outbox.
 * Counts read the indexed database directly to avoid cache-fill versus invalidation races. */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    /** 未读计数缓存时间。短一点更安全：即使缓存失效，也只是多一次 count 查询 */
    private static final Duration COUNT_TTL = Duration.ofMinutes(5);
    private static final String COUNT_KEY = "notify:unread:";

    private final SysNotificationMapper notificationMapper;
    private final CacheService cacheService;
    private final com.zhikao.mapper.NotificationOutboxMapper outbox;

    public NotificationServiceImpl(SysNotificationMapper notificationMapper, CacheService cacheService, com.zhikao.mapper.NotificationOutboxMapper outbox) {
        this.outbox=outbox;
        this.notificationMapper = notificationMapper;
        this.cacheService = cacheService;
    }

    @Override
    public PageResult<NotificationVO> page(int pageNum, int pageSize, boolean unreadOnly) {
        Long userId = LoginUtil.userId();
        LambdaQueryWrapper<SysNotification> wrapper = Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getUserId, userId)
                .eq(unreadOnly, SysNotification::getReadFlag, 0)
                .orderByDesc(SysNotification::getId);
        int size = Math.min(Math.max(pageSize, 1), 100);
        return PageResult.of(
                notificationMapper.selectPage(new Page<>(Math.max(pageNum, 1), size), wrapper),
                this::toVO);
    }

    @Override
    public int unreadCount() {
        Long userId = LoginUtil.userId();
        Long count = notificationMapper.selectCount(Wrappers.<SysNotification>lambdaQuery()
                .eq(SysNotification::getUserId, userId)
                .eq(SysNotification::getReadFlag, 0));
        int value = count == null ? 0 : count.intValue();
        return value;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void read(Long id) {
        Long userId = LoginUtil.userId();
        SysNotification notification = notificationMapper.selectOne(
                Wrappers.<SysNotification>lambdaQuery()
                        .eq(SysNotification::getId, id)
                        .eq(SysNotification::getUserId, userId));
        if (notification == null) {
            // 别人的通知按不存在处理：不报错，也不泄露「这条存在但你没权限」
            return;
        }
        if (notification.getReadFlag() != null && notification.getReadFlag() == 1) {
            return;
        }
        SysNotification update = new SysNotification();
        update.setId(id);
        update.setReadFlag(1);
        update.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(update);
        invalidateAfterCommit(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readAll() {
        Long userId = LoginUtil.userId();
        notificationMapper.update(null,Wrappers.<SysNotification>lambdaUpdate()
                .eq(SysNotification::getUserId,userId).eq(SysNotification::getReadFlag,0)
                .set(SysNotification::getReadFlag,1).set(SysNotification::getReadTime,LocalDateTime.now()));
        invalidateAfterCommit(userId);
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void send(List<Long> userIds,NotificationTypeEnum type,String title,String content,String bizType,Long bizId) {
        if(userIds==null)return;
        var recipients=userIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if(recipients.isEmpty())return;
        String payload=com.zhikao.common.JsonHelper.toJson(recipients);
        String key=java.util.UUID.randomUUID().toString();
        if(bizId!=null && bizType!=null) {
            try {
                String identity=com.zhikao.common.JsonHelper.toJson(java.util.Arrays.asList(type.getCode(),bizType,bizId,title,content,recipients));
                key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        }
        outbox.enqueue(com.zhikao.entity.NotificationOutbox.builder().eventKey(key).userIds(payload)
                .type(type.getCode()).title(title).content(content).bizType(bizType).bizId(bizId).build());
    }
    @Override
    @Transactional(rollbackFor=Exception.class)
    public void sendOne(Long userId,NotificationTypeEnum type,String title,String content,String bizType,Long bizId) {
        if(userId!=null)send(List.of(userId),type,title,content,bizType,bizId);
    }
    private void invalidateAfterCommit(Long userId) {
        Runnable clear=()->{try{cacheService.delete(COUNT_KEY+userId);}catch(Exception ignored){}};
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){clear.run();}});
        } else clear.run();
    }

    private NotificationVO toVO(SysNotification entity) {
        NotificationTypeEnum type = NotificationTypeEnum.of(entity.getType());
        NotificationVO vo = new NotificationVO();
        vo.setId(entity.getId());
        vo.setType(entity.getType());
        vo.setTypeLabel(type.getLabel());
        vo.setIcon(type.getIcon());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setReadFlag(entity.getReadFlag());
        vo.setCreateTime(entity.getCreateTime());
        vo.setReadTime(entity.getReadTime());
        return vo;
    }
}
