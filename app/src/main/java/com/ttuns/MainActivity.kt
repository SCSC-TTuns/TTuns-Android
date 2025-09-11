package com.ttuns

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.ttuns.ui.WebTimetableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WebOnlyApp() }
    }
}

@Composable
private fun WebOnlyApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        WebTimetableScreen(
            url = "https://ttuns.vercel.app/snutt/timetable",
            forceDefaults = true,
            defaultYear = 2025,
            defaultSemesterValue = "3"
        )
    }
}
