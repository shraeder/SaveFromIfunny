package com.ifunnysaver

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 29 && Build.VERSION.SDK_INT >= 23) {
            val granted = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
                return
            }
        }

        Toast.makeText(this, "Use Share from iFunny to save", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQ_STORAGE) return

        val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (ok) {
            Toast.makeText(this, "Permission granted. Now share from iFunny.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Storage permission denied; saving may fail on older Android.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    companion object {
        private const val REQ_STORAGE = 1001
    }
}
