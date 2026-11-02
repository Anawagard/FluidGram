# Experimental AOSP Cuttlefish ARM64 target; NOT flashable to Quest 3.
$(call inherit-product, device/google/cuttlefish/vsoc_arm64/phone/aosp_cf.mk)
PRODUCT_NAME := questfreedom_cf_arm64_phone
PRODUCT_DEVICE := vsoc_arm64
PRODUCT_BRAND := QuestFreedom
PRODUCT_MANUFACTURER := QuestFreedomLab
PRODUCT_MODEL := QuestFreedom ARM64 Virtual
PRODUCT_SOONG_NAMESPACES += device/questfreedom/virtual
PRODUCT_PACKAGES += QuestFreedomLauncher
PRODUCT_SYSTEM_PROPERTIES += ro.questfreedom.prototype=1
