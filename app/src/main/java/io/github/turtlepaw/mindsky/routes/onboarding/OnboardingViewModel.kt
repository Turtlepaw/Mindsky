package io.github.turtlepaw.mindsky.routes.onboarding

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class OnboardingViewModel : ViewModel() {
    var pds by mutableStateOf("")
}

@Composable
fun rememberOnboardingViewModel(): OnboardingViewModel {
    val activity = LocalActivity.current as? ComponentActivity
        ?: throw IllegalStateException("Not in an Activity context")
    return viewModel(activity)
}
