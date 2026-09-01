package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum ProviderPolicy {
    KAKAO("KAKAO"),
    GOOGLE("GOOGLE");

    private final String provider;

    ProviderPolicy(String provider) {
        this.provider = provider;
    }
}
