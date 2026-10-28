package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.user.dto.UserResult;
import org.springframework.stereotype.Service;

@Service
public class GetUserService {

    public UserResult getById(Long id) {
        return new UserResult(id, "stub");
    }
}
