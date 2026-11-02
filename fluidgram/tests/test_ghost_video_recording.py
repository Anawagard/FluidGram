from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")


def test_video_recording_ghost_filter_is_opt_in_and_action_scoped():
    method = privacy.split("public static boolean shouldSuppressRecordingVideo(", 1)[1]
    assert "instanceof TLRPC.TL_messages_setTyping" in method
    assert "instanceof TLRPC.TL_sendMessageRecordVideoAction" in method
    assert "instanceof TLRPC.TL_sendMessageRecordRoundAction" in method
    assert 'enabled("ghost_hide_recording_video")' in method
    assert 'getBoolean(key, false)' in privacy
    assert "TL_sendMessageUploadVideoAction" not in method
    assert "TL_sendMessageUploadRoundAction" not in method
    assert "TL_messages_setEncryptedTyping" not in privacy
    assert "TL_messages_readHistory" not in privacy
    assert "TL_account_updateStatus" not in privacy


def test_video_recording_hook_precedes_native_dispatch():
    hook = patch.split("if (FluidPrivacy.shouldSuppressTyping(object)", 1)[1]
    before_log = hook.split("if (BuildVars.LOGS_ENABLED)", 1)[0]
    assert "|| FluidPrivacy.shouldSuppressRecordingVideo(object)) {" in before_log
    assert "return;" in before_log
    assert "onComplete.run(new TLRPC.TL_boolTrue(), null)" in before_log
    assert "onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)" in before_log


def test_video_recording_switch_is_independent_and_off_by_default():
    assert '"ghost_hide_recording_video", false' in labs
    assert 'Не показывать «записывает видео…»' in labs
    assert 'enabled("ghost_hide_typing")' in privacy
    assert 'enabled("ghost_hide_recording_audio")' in privacy
    assert 'enabled("ghost_hide_recording_video")' in privacy
    assert 'Не блокирует ' in labs
    assert 'Другие клиенты Telegram' in labs
