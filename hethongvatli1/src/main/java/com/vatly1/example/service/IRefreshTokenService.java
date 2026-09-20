package com.vatly1.example.service;


public interface IRefreshTokenService {
    String issue(String username);
    RefreshTokenServiceRotation rotate(String refreshToken);
    void revoke(String refreshToken);
    void deleteAllForUser(String username);

    // Inner record for rotation result
    record RefreshTokenServiceRotation(String username, String newRefreshToken) {}
}