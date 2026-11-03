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
        return typing.action instanceof TLRPC.TL_sendMessageRecordVideoAction
                && enabled("ghost_hide_recording_video");
    }
    /**
     * Suppresses only the outgoing video/round-video upload *activity indicator*.
     * Real media transfers and messages are not affected. Server-side presence,
     * read receipts, and activity from other devices remain unchanged.
     */
    public static boolean shouldSuppressVideoUploadIndicator(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return (typing.action instanceof TLRPC.TL_sendMessageUploadVideoAction
                || typing.action instanceof TLRPC.TL_sendMessageUploadRoundAction)
                && enabled("ghost_hide_video_upload_indicator");
    }
    /**
     * Opt-in suppression of the outgoing document-upload activity indicator.
     * Only messages.setTyping(uploadDocument) is filtered. The actual upload,
     * message delivery, encrypted chats, and server-side presence are untouched.
     */
    public static boolean shouldSuppressDocumentUploadIndicator(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageUploadDocumentAction
                && enabled("ghost_hide_document_upload_indicator");
    }
    /**
     * Opt-in suppression of the ordinary audio-upload activity indicator only.
     * Does not stop the audio transfer, voice message, push, read receipts or presence.
     * Encrypted chats and activity from other devices are not intercepted.
     */
    public static boolean shouldSuppressAudioUploadIndicator(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageUploadAudioAction
                && enabled("ghost_hide_audio_upload_indicator");
    }

    /**
     * Opt-in suppression of the "choosing a sticker" activity indicator.
     * Only ordinary messages.setTyping(chooseSticker) is intercepted.
     * Sticker messages, media transfers, secret chats, read receipts,
     * online presence and actions from other clients are not modified.
     */
    public static boolean shouldSuppressChoosingSticker(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageChooseStickerAction
                && enabled("ghost_hide_choosing_sticker");
    }

    /** Separate opt-in round-video recording indicator filter; media delivery unaffected. */
    public static boolean shouldSuppressRecordingRound(TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)) return false;
        TLRPC.TL_messages_setTyping typing = (TLRPC.TL_messages_setTyping) request;
        return typing.action instanceof TLRPC.TL_sendMessageRecordRoundAction
                && enabled("ghost_hide_recording_round");
    }
}
