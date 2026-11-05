package com.jikchin.jikchin_app.user.controller;

import com.jikchin.jikchin_app.auth.security.CurrentUser;
import com.jikchin.jikchin_app.user.dto.MyProfileResponse;
import com.jikchin.jikchin_app.user.dto.UpdateMyProfileRequest;
import com.jikchin.jikchin_app.user.service.AvatarUploadService;
import com.jikchin.jikchin_app.user.service.MyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
public class MyProfileController {
    private final MyProfileService profiles;
    private final AvatarUploadService avatars;

    @Operation(summary = "마이페이지 프로필 조회", description = "로그인한 본인의 프로필과 응원팀을 반환합니다. 일반 액세스 토큰이 필요합니다.")
    @GetMapping("/profile")
    public MyProfileResponse get(@AuthenticationPrincipal CurrentUser me) {
        return profiles.get(me.userId());
    }

    @Operation(summary = "마이페이지 프로필 수정", description = "프로필 전체를 교체합니다. nickname은 필수이며, avatarKey가 null이면 기본 사진으로, bio와 응원팀 ID가 null이면 선택 해제됩니다. 사진을 유지하려면 조회한 avatarKey를 전달합니다.")
    @PutMapping("/profile")
    public MyProfileResponse update(@AuthenticationPrincipal CurrentUser me,
                                    @RequestBody @Valid UpdateMyProfileRequest request) {
        return profiles.update(me.userId(), request);
    }

    public record NicknameAvailabilityResponse(boolean available) {}
    public record AvatarResponse(String avatarKey) {}

    @Operation(summary = "회원 닉네임 중복 확인", description = "본인의 현재 닉네임은 사용 가능합니다. 앞뒤 공백을 제거하며 최종 중복 검사는 저장 시 수행합니다.")
    @GetMapping("/nickname-availability")
    public NicknameAvailabilityResponse nicknameAvailability(@AuthenticationPrincipal CurrentUser me,
            @RequestParam @NotBlank @Size(max = 20) String nickname) {
        return new NicknameAvailabilityResponse(profiles.isNicknameAvailable(me.userId(), nickname));
    }

    @Operation(summary = "회원 프로필 사진 업로드", description = "5MB, 4096×4096 픽셀 이하 JPEG 파일을 업로드합니다. 반환된 avatarKey를 프로필 수정 API에 전달해야 적용됩니다.")
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public AvatarResponse upload(@AuthenticationPrincipal CurrentUser me, @RequestPart("file") MultipartFile file) {
        try {
            return new AvatarResponse(avatars.upload(me.userId(), file));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
