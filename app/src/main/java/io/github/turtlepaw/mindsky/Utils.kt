package io.github.turtlepaw.mindsky

import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction

fun DestinationsNavigator.replaceCurrent(destination: Direction) {
    popBackStack()
    navigate(destination)
}