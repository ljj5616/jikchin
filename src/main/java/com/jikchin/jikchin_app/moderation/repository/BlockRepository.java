package com.jikchin.jikchin_app.moderation.repository;

import com.jikchin.jikchin_app.moderation.domain.Block;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface BlockRepository extends JpaRepository<Block, Long> {
    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    void deleteByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = {"blocked", "blocked.profile"})
    List<Block> findByBlocker_IdOrderByCreatedAtDescIdDesc(Long blockerId);

    @Query("select b.blocked.id from Block b where b.blocker.id = :viewerId")
    Set<Long> findBlockedUserIds(@Param("viewerId") Long viewerId);

    @Query("""
            select count(b) > 0 from Block b
            where (b.blocker.id = :firstId and b.blocked.id = :secondId)
               or (b.blocker.id = :secondId and b.blocked.id = :firstId)
            """)
    boolean existsBetween(@Param("firstId") Long firstId, @Param("secondId") Long secondId);
}
