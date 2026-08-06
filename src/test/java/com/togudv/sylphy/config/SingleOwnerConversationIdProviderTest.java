package com.togudv.sylphy.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleOwnerConversationIdProviderTest {

    @Test
    void getConversationId_returnsConfiguredValue() {
        SingleOwnerConversationIdProvider provider = new SingleOwnerConversationIdProvider("owner-7");

        assertEquals("owner-7", provider.getConversationId());
    }

    @Test
    void getConversationId_usesDefaultWhenUnset() {
        SingleOwnerConversationIdProvider provider = new SingleOwnerConversationIdProvider(null);

        assertEquals("owner-1", provider.getConversationId());
    }
}
