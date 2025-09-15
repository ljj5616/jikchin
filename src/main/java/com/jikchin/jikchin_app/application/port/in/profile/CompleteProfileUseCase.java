package com.jikchin.jikchin_app.application.port.in.profile;

import com.jikchin.jikchin_app.application.dto.auth.CompleteProfileRequest;
import com.jikchin.jikchin_app.application.dto.auth.CompleteProfileResponse;

public interface CompleteProfileUseCase {
    CompleteProfileResponse completeProfile(Long userId, CompleteProfileRequest request);
}
