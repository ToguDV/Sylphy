package com.togudv.sylphy.config;

public interface NotificationDestination {

    String value();

    Channel channel();

    enum Channel {
        TELEGRAM
    }
}
