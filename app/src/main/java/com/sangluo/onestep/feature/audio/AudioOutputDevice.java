package com.sangluo.onestep.feature.audio;

import android.media.AudioDeviceInfo;
import android.text.TextUtils;

import java.util.Locale;

/** One selectable audio output plus the identity used to re-apply it after reconnects. */
public final class AudioOutputDevice {
    public final int type;
    /** Name understood by {@code cmd audio set-preferred-output-device}. */
    public final String typeName;
    public final String typeLabel;
    public final String address;
    /** Product name when the system reports one, otherwise the type label. */
    public final String name;

    public AudioOutputDevice(int type, String typeName, String typeLabel,
                             String address, String name) {
        this.type = type;
        this.typeName = typeName;
        this.typeLabel = typeLabel;
        this.address = AudioOutputRoutingClient.isRedactedAddress(address)
                ? "" : address.trim();
        this.name = TextUtils.isEmpty(name) ? typeLabel : name;
    }

    /** Returns {@code null} for inputs and for outputs the shell command cannot select. */
    public static AudioOutputDevice from(AudioDeviceInfo info) {
        if (info == null || info.isSource()) {
            return null;
        }
        String typeName = AudioOutputTypes.commandName(info.getType());
        if (typeName == null) {
            return null;
        }
        String typeLabel = AudioOutputTypes.label(info.getType());
        CharSequence product = info.getProductName();
        String productName = product == null ? "" : product.toString().trim();
        String name = TextUtils.isEmpty(productName)
                || "unknown".equalsIgnoreCase(productName) ? typeLabel : productName;
        String address = info.getAddress() == null ? "" : info.getAddress().trim();
        return new AudioOutputDevice(info.getType(), typeName, typeLabel, address, name);
    }

    /** Stable identity across reconnects: audio devices keep type and hardware address. */
    public String key() {
        return typeName + "|" + address.toLowerCase(Locale.US);
    }

    public boolean matches(AudioDeviceInfo info) {
        if (info == null || info.getType() != type) {
            return false;
        }
        String raw = info.getAddress() == null ? "" : info.getAddress().trim();
        String other = AudioOutputRoutingClient.isRedactedAddress(raw) ? "" : raw;
        return address.equalsIgnoreCase(other);
    }

    public boolean matchesKey(String otherKey) {
        return otherKey != null && otherKey.equalsIgnoreCase(key());
    }

    public String subtitle() {
        if (TextUtils.isEmpty(address) || !typeName.startsWith("BLUETOOTH")) {
            return typeLabel;
        }
        return typeLabel + " · " + address;
    }
}
