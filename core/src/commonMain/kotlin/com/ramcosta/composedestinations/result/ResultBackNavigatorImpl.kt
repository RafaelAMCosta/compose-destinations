package com.ramcosta.composedestinations.result

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.ramcosta.composedestinations.navargs.DestinationsNavType
import com.ramcosta.composedestinations.spec.DestinationSpec
import kotlin.reflect.KClass

internal class ResultBackNavigatorImpl<R>(
    private val navController: NavController,
    resultOriginType: KClass<out DestinationSpec>,
    private val resultNavType: DestinationsNavType<in R>
) : ResultBackNavigator<R> {

    private val resultKey = resultKey(resultOriginType, resultNavType)
    private val canceledKey = canceledKey(resultOriginType, resultNavType)

    override fun navigateBack(result: R) {
        setResult(result)
        navigateBack()
    }

    override fun setResult(result: R) {
        navController.previousBackStackEntry?.savedStateHandle?.let {
            it[canceledKey] = false
            resultNavType.put(it, resultKey, result)
        }
    }

    override fun navigateBack() {
        navController.navigateUp()
    }

    @SuppressLint("ComposableNaming")
    @Composable
    fun handleCanceled() {
        val currentNavBackStackEntry = remember { navController.currentBackStackEntry } ?: return

        DisposableEffect(key1 = Unit) {
            val observer = object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            val savedStateHandle =
                                navController.previousBackStackEntry?.savedStateHandle ?: return

                            if (!savedStateHandle.contains(canceledKey)) {
                                // We set canceled to true when this destination becomes visible
                                // When a value to be returned is set, we will put the canceled to `false`
                                savedStateHandle[canceledKey] = true
                                currentNavBackStackEntry.lifecycle.removeObserver(this)
                            }
                        }

                        else -> Unit
                    }
                }
            }

            currentNavBackStackEntry.lifecycle.addObserver(observer)

            onDispose {
                currentNavBackStackEntry.lifecycle.removeObserver(observer)
            }
        }
    }
}