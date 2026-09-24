package com.ruoyi.aden.domain.event;

/** Outbox 投递生命周期；只有已领取消息才能确认或失败回退。 */
public enum AdenOutboxState {
    PENDING,
    CLAIMED,
    PUBLISHED,
    DEAD
}
