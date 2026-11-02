package com.palmlink.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgBase = Color(0xFF0B0E12)
private val Surface1 = Color(0xFF151B22)
private val Primary = Color(0xFF2ED3C6)
private val OnPrimary = Color(0xFF04201D)
private val TextMain = Color(0xFFE8F0F7)
private val TextMuted = Color(0xFF8B9DAD)
private val TextDim = Color(0xFF5A6A7A)

private val PalmColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = BgBase,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain
)

@Composable
fun PalmLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PalmColorScheme, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PalmLinkTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
                    BootScreen()
                }
            }
        }
    }
}

@Composable
private fun BootScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .background(Primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "掌",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "掌驭",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMain,
            letterSpacing = 4.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "P A L M L I N K",
            fontSize = 10.sp,
            color = TextDim,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(30.dp))

        Text(
            text = "工程骨架已跑通\n下一步接入 WebRTC 屏幕流",
            fontSize = 12.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .background(Surface1, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Text(
                text = "v0.1.0 · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                    "${Build.MANUFACTURER} ${Build.MODEL}",
                fontSize = 10.5.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E12)
@Composable
private fun BootScreenPreview() {
    PalmLinkTheme { BootScreen() }
}
