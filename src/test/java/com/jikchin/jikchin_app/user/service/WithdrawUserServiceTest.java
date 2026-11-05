package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.auth.infrastructure.session.RefreshSessionRepository;
import com.jikchin.jikchin_app.chat.repository.ChatParticipantRepository;
import com.jikchin.jikchin_app.moderation.repository.BlockRepository;
import com.jikchin.jikchin_app.record.repository.RecordRepository;
import com.jikchin.jikchin_app.room.repository.RoomRepository;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import com.jikchin.jikchin_app.user.domain.User;
import com.jikchin.jikchin_app.user.domain.UserStatus;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import com.jikchin.jikchin_app.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawUserServiceTest {
    @Mock UserRepository users;
    @Mock ProfileRepository profiles;
    @Mock RefreshSessionRepository sessions;
    @Mock RoomRepository rooms;
    @Mock LeaveRoomService leaveRooms;
    @Mock ChatParticipantRepository participants;
    @Mock BlockRepository blocks;
    @Mock RecordRepository records;
    @InjectMocks WithdrawUserService service;

    @Test
    void concurrentMembershipChangeReturnsConflictBeforeDeletingAnyData() {
        var user = User.createSocialUser("google", "original-identity");
        user.activate();
        when(rooms.findWithdrawalRoomIds(7L)).thenReturn(List.of()).thenReturn(List.of(9L));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(users.findStatusById(7L)).thenReturn(Optional.of(UserStatus.ACTIVE));

        assertThatThrownBy(() -> service.withdraw(7L)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getProviderId()).isEqualTo("original-identity");
        verifyNoInteractions(leaveRooms, participants, sessions, blocks, records, profiles);
        verify(rooms, never()).findByIdForUpdate(any());
    }

    @Test
    void aRequestThatWaitedForAnotherWithdrawalDoesNotMutateTheDeletedAccount() {
        // Simulate an active entity loaded by the filter before another transaction committed deletion.
        var stale = User.createSocialUser("google", "stale");
        stale.activate();
        when(rooms.findWithdrawalRoomIds(7L)).thenReturn(List.of());
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(stale));
        when(users.findStatusById(7L)).thenReturn(Optional.of(UserStatus.DELETED));

        service.withdraw(7L);

        verifyNoInteractions(leaveRooms, participants, sessions, blocks, records, profiles);
        verify(users, never()).flush();
    }
}
