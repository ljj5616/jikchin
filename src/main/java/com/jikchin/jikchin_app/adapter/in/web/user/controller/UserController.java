package com.jikchin.jikchin_app.adapter.in.web.user.controller;

import com.jikchin.jikchin_app.adapter.in.web.security.CurrentUser;
import com.jikchin.jikchin_app.application.dto.user.UserResult;
import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import com.jikchin.jikchin_app.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final GetUserUseCase getUserUseCase;

    @GetMapping("/me")
    public UserResult me(@AuthenticationPrincipal CurrentUser me) {
        return getUserUseCase.getById(me.userId());
    }
}
