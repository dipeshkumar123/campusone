package com.campusone.api.dto;

public record LoginResponse(String token, UserView user) {
}
