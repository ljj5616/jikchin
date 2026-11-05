package com.jikchin.jikchin_app.user.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.dto.UserHttpResponse;
import com.jikchin.jikchin_app.user.dto.UserResult;
import com.jikchin.jikchin_app.user.service.GetUserService;
import com.jikchin.jikchin_app.user.service.WithdrawUserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final GetUserService getUserService;
    private final WithdrawUserService withdrawUserService;

    @Operation(summary = "회원 탈퇴", description = "본인 계정을 탈퇴하고 모든 로그인 세션과 프로필을 정리합니다.")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CurrentUser me) {
        withdrawUserService.withdraw(me.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserHttpResponse> me(@AuthenticationPrincipal CurrentUser me) {
        UserResult result = getUserService.getById(me.userId());
        return ResponseEntity.ok(new UserHttpResponse(result.id(), result.nickname()));
    }
}
