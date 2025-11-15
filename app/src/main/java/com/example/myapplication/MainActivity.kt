package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme


import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue // Import for the delegated property

// ... (other imports from your original MainActivity)

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // 1. Logic to switch to the Landing Screen after 3 seconds
    LaunchedEffect(key1 = true) {
        delay(3000L) // 3 seconds delay
        onTimeout()
    }

    // 2. State for the pulse animation
    val pulseState = remember { mutableStateOf(false) }

    // 3. Start the pulse immediately and infinitely repeat it
    LaunchedEffect(key1 = true) {
        pulseState.value = true
    }

    // 4. Define the animated scale value
    val scale by animateFloatAsState(
        targetValue = if (pulseState.value) 1.1f else 1.0f, // Scale up to 110%
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse // Go back and forth (pulsate)
        ), label = "logoScale"
    )

    // Design the logo screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E2A38)), // Dark background
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Image for the logo, using the calculated 'scale'
        Image(
            painter = painterResource(id = R.drawable.app_logo), // **Remember to add app_logo.png to res/drawable**
            contentDescription = "App Logo",
            modifier = Modifier
                .size(120.dp)
                .scale(scale) // Apply the animation scale
        )

        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(color = Color.White)
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hellofgh $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}