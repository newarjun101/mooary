package com.arjun.mooary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arjun.mooary.ui.App

/**
 * The single activity that hosts the shared Compose UI.
 *
 * `configChanges` in the manifest keeps the activity alive across rotation, theme changes
 * and keyboard resizes, so those do not tear down the Koin graph or the session restore that
 * is already in flight.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
