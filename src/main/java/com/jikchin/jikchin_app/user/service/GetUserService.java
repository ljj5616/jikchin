package com.jikchin.jikchin_app.user.service;

import com.jikchin.jikchin_app.user.dto.UserResult;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import com.jikchin.jikchin_app.user.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GetUserService {
    private final ProfileRepository profiles;

    public UserResult getById(Long id) {
        var profile = profiles.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new UserResult(id, profile.getNickname());
    }
}
