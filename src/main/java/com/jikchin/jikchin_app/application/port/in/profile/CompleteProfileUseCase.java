package com.jikchin.jikchin_app.application.port.in.profile;

import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileRequest;
import com.jikchin.jikchin_app.application.dto.profile.CompleteProfileResponse;

public interface CompleteProfileUseCase {
    CompleteProfileResponse completeProfile(Long userId, CompleteProfileRequest request);
}
