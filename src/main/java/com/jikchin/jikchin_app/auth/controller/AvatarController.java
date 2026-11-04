package com.jikchin.jikchin_app.auth.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.service.AvatarUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/avatar")
public class AvatarController {
    private final AvatarUploadService avatars;

    public record AvatarResponse(String avatarKey) {}

    @PostMapping(consumes = "multipart/form-data")
    public AvatarResponse upload(@AuthenticationPrincipal CurrentUser user, @RequestPart("file") MultipartFile file) {
        try {
            return new AvatarResponse(avatars.upload(user.userId(), file));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
