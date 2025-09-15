package com.jikchin.jikchin_app.adapter.in.web.user.controller;

import com.jikchin.jikchin_app.application.dto.user.UserResult;
import com.jikchin.jikchin_app.application.port.in.user.GetUserUseCase;
import com.jikchin.jikchin_app.domain.user.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final GetUserUseCase getUserUseCase;

    public UserController(GetUserUseCase getUserUseCase) {
        this.getUserUseCase = getUserUseCase;
    }

    @GetMapping("/{id}")
    public UserResult get(@PathVariable Long id) {
        return getUserUseCase.getById(id);
    }
}
