package com.yche.springmind.auth.model.vo;

import com.yche.springmind.auth.service.AuthService.AuthTokens;
import com.yche.springmind.identity.service.CurrentUserService;

public record AuthTokensResponse(
        String accessToken,
        CurrentUserProfileResponse currentUser
) {

    public static AuthTokensResponse from(AuthTokens tokens, CurrentUserService.CurrentUser currentUser) {
        return new AuthTokensResponse(tokens.accessToken(), CurrentUserProfileResponse.from(currentUser));
    }
}
