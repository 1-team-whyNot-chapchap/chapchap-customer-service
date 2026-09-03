package com.chapchap.customer.global.error.custom.notification;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;

public class NotificationNotFoundException extends BusinessException {
    public NotificationNotFoundException() {
        super(CustomResponseCode.NOT_FOUND_RESOURCE_ERROR, "알림을 찾을 수 없습니다.");
    }
}
