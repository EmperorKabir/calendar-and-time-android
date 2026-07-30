package com.kabirbhasin.statuscalendar.slot

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView

/**
 * A slot app is otherwise only a receiver, but Android 13 and newer will not grant
 * the notification permission to a package the user cannot open. This screen exists
 * to request that permission and to explain what the slot is for.
 */
class SlotActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.slot_explanation)
            textSize = 16f
            setPadding(48, 96, 48, 48)
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
