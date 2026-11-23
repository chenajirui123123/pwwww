package com.wardrobe.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    public record RegisterRequest(String accountName, String password, String displayName) {}
    public record LoginRequest(String accountName, String password) {}
    public record ProfileRequest(String displayName) {}
    public record UserResponse(User user) {}

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody RegisterRequest request) {
        return new UserResponse(accounts.register(request.accountName(), request.password(), request.displayName()));
    }

    @PostMapping("/auth/login")
    public AccountService.LoginResult login(@RequestBody LoginRequest request) {
        return accounts.login(request.accountName(), request.password());
    }

    @GetMapping("/me")
    public UserResponse profile(@RequestHeader(value="Authorization", required=false) String authorization) {
        return new UserResponse(accounts.requireUser(authorization));
    }

    @PatchMapping("/me")
    public UserResponse update(@RequestHeader(value="Authorization", required=false) String authorization,
            @RequestBody ProfileRequest request) {
        return new UserResponse(accounts.updateProfile(authorization, request.displayName()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value="Authorization", required=false) String authorization) {
        accounts.logout(authorization);
    }
}
