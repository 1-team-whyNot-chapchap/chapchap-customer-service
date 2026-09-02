package com.chapchap.customer.global.security.context;

import com.chapchap.customer.global.security.constant.RolePolicy;

import java.security.Principal;

public record GatewayUserPrincipal(
        String userId,
        RolePolicy role
) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
