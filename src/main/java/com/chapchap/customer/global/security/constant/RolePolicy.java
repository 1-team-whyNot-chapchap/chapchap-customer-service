package com.chapchap.customer.global.security.constant;

import lombok.Getter;

@Getter
public enum RolePolicy {
    CUSTOMER("CUSTOMER"),
    RIDER("RIDER"),
    ADMIN("ADMIN"),
    SUPER_ADMIN("SUPER_ADMIN")
    ;

    private final String role;

    RolePolicy(String role) {
        this.role = role;
    }
}
