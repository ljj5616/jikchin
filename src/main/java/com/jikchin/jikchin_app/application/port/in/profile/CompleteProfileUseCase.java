package com.jikchin.jikchin_app.application.port.in.profile;

import com.jikchin.jikchin_app.adapter.in.web.profile.dto.CompleteProfileHttpRequest;
import com.jikchin.jikchin_app.adapter.in.web.profile.dto.CompleteProfileHttpResponse;

public interface CompleteProfileUseCase {
    CompleteProfileResult completeProfile(CompleteProfileCommand command);
}
