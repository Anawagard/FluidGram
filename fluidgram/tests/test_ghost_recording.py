from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")


def test_recording_indicator_is_opt_in_and_audio_only():
    assert 'shouldSuppressRecordingAudio(TLObject request)' in privacy
    recording = privacy.split('public static boolean shouldSuppressRecordingAudio(', 1)[1]
    assert 'instanceof TLRPC.TL_messages_setTyping' in recording
    assert 'instanceof TLRPC.TL_sendMessageRecordAudioAction' in recording
    assert 'enabled("ghost_hide_recording_audio")' in recording
    assert 'TL_sendMessageUploadAudioAction' not in recording.split('    }', 1)[0]
    assert 'TL_sendMessageRecordVideoAction' not in recording.split('    }', 1)[0]
    assert 'TL_messages_setEncryptedTyping' not in privacy
    assert 'TL_account_updateStatus' not in privacy
    assert 'TL_messages_readHistory' not in privacy


def test_recording_and_typing_have_independent_controls():
    assert 'enabled("ghost_hide_typing")' in privacy
    assert 'enabled("ghost_hide_recording_audio")' in privacy
    assert 'getBoolean(key, false)' in privacy
    assert '"ghost_hide_recording_audio", false' in labs
    assert 'Не показывать «записывает голосовое…»' in labs
    assert 'не скрывает видеозапись, онлайн, прочтение' in labs


def test_recording_filter_runs_before_native_request():
    assert 'FluidPrivacy.shouldSuppressRecordingAudio(object)' in patch
    hook = patch.split('if (FluidPrivacy.shouldSuppressTyping(object)', 1)[1]
    assert '|| FluidPrivacy.shouldSuppressRecordingAudio(object)' in hook
    assert 'return;' in hook.split('if (BuildVars.LOGS_ENABLED)', 1)[0]
    assert 'onComplete.run(new TLRPC.TL_boolTrue(), null)' in hook
    assert 'onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)' in hook
