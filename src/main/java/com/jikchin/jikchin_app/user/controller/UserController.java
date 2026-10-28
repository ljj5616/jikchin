package com.jikchin.jikchin_app.user.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.dto.UserHttpResponse;
import com.jikchin.jikchin_app.user.dto.UserResult;
import com.jikchin.jikchin_app.user.service.GetUserService;
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
    private final GetUserService getUserService;

    @GetMapping("/me")
    public ResponseEntity<UserHttpResponse> me(@AuthenticationPrincipal CurrentUser me) {
        UserResult result = getUserService.getById(me.userId());
        return ResponseEntity.ok(new UserHttpResponse(result.id(), result.nickname()));
    }
}
