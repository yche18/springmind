package com.yche.springmind.user.controller;

import com.yche.springmind.common.api.ApiResponse;
import com.yche.springmind.identity.service.CurrentUserService;
import com.yche.springmind.user.model.dto.ChangePasswordRequest;
import com.yche.springmind.user.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    public AccountController(
            AccountService accountService,
            CurrentUserService currentUserService
    ) {
        this.accountService = accountService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        accountService.changePassword(currentUserService.getRequiredCurrentUser(httpServletRequest), request);
        return ApiResponse.success(null);
    }
}
