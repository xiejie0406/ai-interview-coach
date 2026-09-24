package com.ruoyi.aden.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdenOutboxMapper {
    List<AdenOutboxRow> selectReadyForUpdate(@Param("now") LocalDateTime now,
                                             @Param("limit") int limit);

    int claim(@Param("workspaceId") String workspaceId,
              @Param("outboxId") String outboxId,
              @Param("claimToken") String claimToken,
              @Param("now") LocalDateTime now,
              @Param("claimedUntil") LocalDateTime claimedUntil);

    AdenOutboxRow selectClaimed(@Param("workspaceId") String workspaceId,
                                @Param("outboxId") String outboxId,
                                @Param("claimToken") String claimToken);

    int markPublished(@Param("workspaceId") String workspaceId,
                      @Param("outboxId") String outboxId,
                      @Param("claimToken") String claimToken,
                      @Param("publishedAt") LocalDateTime publishedAt);

    int markRetry(@Param("workspaceId") String workspaceId,
                  @Param("outboxId") String outboxId,
                  @Param("claimToken") String claimToken,
                  @Param("error") String error,
                  @Param("nextAvailableAt") LocalDateTime nextAvailableAt,
                  @Param("updatedAt") LocalDateTime updatedAt);

    int markDead(@Param("workspaceId") String workspaceId,
                 @Param("outboxId") String outboxId,
                 @Param("claimToken") String claimToken,
                 @Param("error") String error,
                 @Param("updatedAt") LocalDateTime updatedAt);
}
