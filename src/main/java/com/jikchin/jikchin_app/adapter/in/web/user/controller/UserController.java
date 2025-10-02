package com.jikchin.jikchin_app.adapter.in.web.user.controller;

import com.jikchin.jikchin_app.adapter.in.web.security.CurrentUser;
import com.jikchin.jikchin_app.adapter.in.web.user.dto.UserHttpResponse;
import com.jikchin.jikchin_app.application.port.in.user.UserResult;
import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final GetUserUseCase getUserUseCase;

    @GetMapping("/me")
    public ResponseEntity<UserHttpResponse> me(@AuthenticationPrincipal CurrentUser me) {
        UserResult result = getUserUseCase.getById(me.userId());
        return ResponseEntity.ok(new UserHttpResponse(result.id(), result.nickname()));
    }
}
