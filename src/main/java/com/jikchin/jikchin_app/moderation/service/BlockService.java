package com.jikchin.jikchin_app.moderation.service;

import com.jikchin.jikchin_app.moderation.domain.Block;
import com.jikchin.jikchin_app.moderation.dto.BlockedUserResponse;
import com.jikchin.jikchin_app.moderation.repository.BlockRepository;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {
    private final BlockRepository blocks;
    private final UserRepository users;

    @Value("${app.assets.base-url}") private String assetsBaseUrl;
    @Value("${app.assets.default-avatar-key}") private String defaultAvatarKey;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void block(Long userId, Long targetId) {
        validateTarget(userId, targetId);
        User blocker = lockActiveUserAndTarget(userId, targetId);
        User target = users.findById(targetId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (!blocks.existsByBlocker_IdAndBlocked_Id(userId, targetId)) {
            blocks.saveAndFlush(Block.create(blocker, target));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void unblock(Long userId, Long targetId) {
        validateTarget(userId, targetId);
        lockActiveUserAndTarget(userId, targetId);
        blocks.deleteByBlocker_IdAndBlocked_Id(userId, targetId);
    }

    public List<BlockedUserResponse> list(Long userId) {
        return blocks.findByBlocker_IdOrderByCreatedAtDescIdDesc(userId).stream().map(block -> {
            var user = block.getBlocked();
            var profile = user.getProfile();
            String key = profile == null ? defaultAvatarKey : profile.getAvatarKey();
            String avatarUrl = UriComponentsBuilder.fromUriString(assetsBaseUrl)
                    .pathSegment(key.split("/")).build().encode().toUriString();
            return new BlockedUserResponse(user.getId(),
                    BlockedNickname.format(profile == null ? null : profile.getNickname(), true),
                    avatarUrl, block.getCreatedAt());
        }).toList();
    }

    public Set<Long> blockedUserIds(Long viewerId) {
        return blocks.findBlockedUserIds(viewerId);
    }

    // Delivery may run after a chat transaction commits. Read the latest block state separately.
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean hasBlocked(Long viewerId, Long targetId) {
        return !viewerId.equals(targetId) && blocks.existsByBlocker_IdAndBlocked_Id(viewerId, targetId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void requireCanJoin(Long userId, Long creatorId) {
        lockActiveUserAndTarget(userId, creatorId);
        if (userId.equals(creatorId)) return;
        if (blocks.existsBetween(userId, creatorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "차단 관계인 사용자가 만든 방에는 참여할 수 없습니다.");
        }
    }

    private User lockActiveUserAndTarget(Long userId, Long targetId) {
        // Use the same order for block mutations, FK checks and room joins, including opposite requests.
        var first = users.findByIdForUpdate(Math.min(userId, targetId));
        var second = users.findByIdForUpdate(Math.max(userId, targetId));
        User user = (userId < targetId ? first : second)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (users.findStatusById(userId).orElseThrow() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "프로필 등록을 완료해 주세요.");
        }
        return user;
    }

    private void validateTarget(Long userId, Long targetId) {
        if (targetId == null || targetId < 1 || userId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인이 아닌 사용자를 지정해 주세요.");
        }
    }
}
