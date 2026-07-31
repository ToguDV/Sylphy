package com.togudv.sylphy.service.notification;

import com.togudv.sylphy.model.Reminder;

public interface NotificationDispatcher {

    void dispatch(Reminder reminder);
}
