/* GPL-2.0-or-later. FluidGram Ghost Lab: optional outgoing activity filters. */
package org.telegram.tgnet;

import android.content.Context;
import org.telegram.messenger.ApplicationLoader;

/**
 * Applies only to ordinary messages.setTyping with explicitly selected actions.
 * Does not intercept encrypted chats, actual file uploads, presence, or read receipts.
 * Disabled by default and never mutates credentials or incoming push delivery.
 */
public final class FluidPrivacy {
    private FluidPrivacy() {}

    private static boolean enabled(String key) {
        Context context = ApplicationLoader.applicationContext;
        return context != null && context.getSharedPreferences("fluidgram_labs",
                Context.MODE_PRIVATE).getBoolean(key, false);
    }

    public static boolean shouldSuppressTyping(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageTypingAction
                && enabled("ghost_hide_typing");
    }

    /** Audio recording indicator only; uploading and sending voice notes still work. */
    public static boolean shouldSuppressRecordingAudio(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageRecordAudioAction
                && enabled("ghost_hide_recording_audio");
    }

    /** Optional photo-upload activity only, never the media upload or message send. */
    public static boolean shouldSuppressPhotoUploadIndicator(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageUploadPhotoAction
                && enabled("ghost_hide_photo_upload_indicator");
    }

    /** Ordinary video and round-video recording indicators, not uploads or calls. */
    public static boolean shouldSuppressRecordingVideo(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return (typing.action instanceof TLRPC.TL_sendMessageRecordVideoAction
                || typing.action instanceof TLRPC.TL_sendMessageRecordRoundAction)
                && enabled("ghost_hide_recording_video");
    }
}
