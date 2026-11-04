package com.jikchin.jikchin_app.room.controller;
import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.room.dto.CreateRoomRequest;
import com.jikchin.jikchin_app.room.dto.RoomResponse;
import com.jikchin.jikchin_app.room.service.CreateRoomService;
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
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateRoomRequest request) {
        return createRoomService.create(user.userId(), request);
    }
    @GetMapping("/mine")
    public List<RoomResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return createRoomService.mine(user.userId());
    }
}
