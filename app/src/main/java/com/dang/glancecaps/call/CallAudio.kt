package com.dang.glancecaps.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

object CallAudio {
    private fun manager(ctx: Context): AudioManager? = ctx.getSystemService(AudioManager::class.java)

    @Suppress("DEPRECATION")
    fun speakerOn(ctx: Context): Boolean {
        val am = manager(ctx) ?: return false
        return try {
            when {
                Build.VERSION.SDK_INT >= 33 -> {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    am.getAudioDevicesForAttributes(attrs).any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                }
                Build.VERSION.SDK_INT >= 31 -> am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                else -> am.isSpeakerphoneOn
            }
        } catch (e: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    fun toggle(ctx: Context) {
        val am = manager(ctx) ?: return
        val enable = !speakerOn(ctx)
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (enable) {
                    val speaker = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) am.setCommunicationDevice(speaker)
                } else {
                    am.clearCommunicationDevice()
                }
            } else {
                am.isSpeakerphoneOn = enable
            }
        } catch (e: Throwable) {
        }
    }
}
