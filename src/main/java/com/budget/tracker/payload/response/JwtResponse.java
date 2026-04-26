package com.budget.tracker.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private UUID id;
    private String email;

    public JwtResponse(String accessToken, UUID id, String email) {
        this.token = accessToken;
        this.id = id;
        this.email = email;
    }
}
