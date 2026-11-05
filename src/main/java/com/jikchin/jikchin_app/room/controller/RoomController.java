package com.jikchin.jikchin_app.room.controller;
import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
import com.jikchin.jikchin_app.room.service.GetRoomService;
import com.jikchin.jikchin_app.room.service.JoinRoomService;
import com.jikchin.jikchin_app.room.service.LeaveRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final CreateRoomService createRoomService;
    private final GetRoomService getRoomService;
    private final JoinRoomService joinRoomService;
    private final LeaveRoomService leaveRoomService;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateRoomRequest request) {
        return createRoomService.create(user.userId(), request);
    }
    @GetMapping
    public List<RoomResponse> list() {
        return getRoomService.list();
    }
    @GetMapping("/mine")
    public List<RoomResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return getRoomService.mine(user.userId());
    }
    @PostMapping("/{roomId}/participants")
    public RoomResponse join(@AuthenticationPrincipal CurrentUser user, @PathVariable Long roomId) {
        return joinRoomService.join(user.userId(), roomId);
    }
    @DeleteMapping("/{roomId}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal CurrentUser user, @PathVariable Long roomId) {
        leaveRoomService.leave(user.userId(), roomId);
    }
}
