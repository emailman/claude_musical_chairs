@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        TestApp()
    }
}

@Composable
fun TestApp() {
    var count by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C3E50)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Musical Chairs Test",
                color = Color.White,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Count: $count",
                color = Color.White,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = { count++ }) {
                Text("Click Me")
            }
        }
    }
}
