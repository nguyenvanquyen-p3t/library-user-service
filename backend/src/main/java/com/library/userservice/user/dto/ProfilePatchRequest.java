package com.library.userservice.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.library.userservice.common.validation.Normalization;
import com.library.userservice.common.validation.ValidProfilePatch;

@ValidProfilePatch
public final class ProfilePatchRequest {
    private String fullName;
    private String phoneNumber;
    private boolean fullNamePresent;
    private boolean phoneNumberPresent;

    @JsonSetter("fullName")
    public void setFullName(String value) { fullNamePresent = true; fullName = Normalization.fullName(value); }
    @JsonSetter("phoneNumber")
    public void setPhoneNumber(String value) { phoneNumberPresent = true; phoneNumber = value; }
    @JsonProperty("fullName") public String getFullName() { return fullName; }
    @JsonProperty("phoneNumber") public String getPhoneNumber() { return phoneNumber; }
    @JsonIgnore public boolean hasFullName() { return fullNamePresent; }
    @JsonIgnore public boolean hasPhoneNumber() { return phoneNumberPresent; }
}

