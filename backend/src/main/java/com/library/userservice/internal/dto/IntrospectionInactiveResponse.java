package com.library.userservice.internal.dto;

public record IntrospectionInactiveResponse() {
    @com.fasterxml.jackson.annotation.JsonProperty("active")
    public boolean active() { return false; }
}

