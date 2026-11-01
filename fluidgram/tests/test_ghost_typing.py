from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
privacy = (ROOT / "android/FluidPrivacy.java").read_text(encoding="utf-8")
patch = (ROOT / "scripts/apply_fluidgram.py").read_text(encoding="utf-8")
labs = (ROOT / "android/FluidLabsActivity.java").read_text(encoding="utf-8")

def test_ghost_typing_is_opt_in_and_narrowly_scoped():
    assert 'instanceof TLRPC.TL_messages_setTyping' in privacy
    assert 'instanceof TLRPC.TL_sendMessageTypingAction' in privacy
    assert 'getBoolean("ghost_hide_typing", false)' in privacy
    assert 'TL_messages_readHistory' not in privacy
    assert 'TL_account_updateStatus' not in privacy
    assert 'TL_messages_setEncryptedTyping' not in privacy

def test_outgoing_rpc_intercept_before_serialization():
    assert '"FluidPrivacy.java"' in patch
    assert 'org/telegram/tgnet/FluidPrivacy.java' in patch
    assert 'FluidPrivacy.shouldSuppressTyping(object)' in patch
    hook = patch.split('if (FluidPrivacy.shouldSuppressTyping(object)) {', 1)[1]
    assert 'return;' in hook.split('if (BuildVars.LOGS_ENABLED)', 1)[0]
    assert 'onComplete.run(new TLRPC.TL_boolTrue(), null)' in hook
    assert 'onCompleteTimestamp.run(new TLRPC.TL_boolTrue(), null, 0L)' in hook
    assert 'NativeByteBuffer buffer = new NativeByteBuffer(object.getObjectSize());' in patch or 'No native_sendRequest' in patch

def test_user_can_disable_and_understands_limits():
    assert 'toggle("Не показывать «печатает…»"' in labs
    assert '"ghost_hide_typing", false' in labs
    assert 'Не скрывает прочтение, онлайн, запись, загрузку' in labs
