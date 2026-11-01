/* GPL-2.0-or-later. FluidGram Ghost Lab: optional outgoing typing-only filter. */
package org.telegram.tgnet;

import android.content.Context;
import org.telegram.messenger.ApplicationLoader;

/**
 * Applies only to the ordinary messages.setTyping / sendMessageTypingAction pair.
 * Does not intercept encrypted chats, recording/upload actions, presence, or reads.
 * Disabled by default and never mutates credentials or incoming push delivery.
 */
public final class FluidPrivacy {
    private FluidPrivacy() {}

    public static boolean shouldSuppressTyping(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        if (!(typing.action instanceof TLRPC.TL_sendMessageTypingAction)) return false;
        Context context = ApplicationLoader.applicationContext;
        return context != null && context.getSharedPreferences("fluidgram_labs",
                Context.MODE_PRIVATE).getBoolean("ghost_hide_typing", false);
    }
}
