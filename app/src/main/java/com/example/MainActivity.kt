package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.repository.CloudVaultClient
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.VaultRepository
import com.example.ui.calculator.SubnetCalculatorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.vault.VaultLockScreen
import com.example.ui.vault.VaultMainScreen

enum class ScreenState {
    CALCULATOR,
    VAULT_LOCK,
    VAULT_MAIN
}

class MainActivity : ComponentActivity() {

    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var vaultRepo: VaultRepository
    private val cloudClient = CloudVaultClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        userPrefs = UserPreferencesRepository(applicationContext)
        vaultRepo = VaultRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) {
                    AppNavigation(
                        userPrefs = userPrefs,
                        vaultRepo = vaultRepo,
                        cloudClient = cloudClient
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    userPrefs: UserPreferencesRepository,
    vaultRepo: VaultRepository,
    cloudClient: CloudVaultClient
) {
    var screenState by remember { mutableStateOf(ScreenState.CALCULATOR) }

    // When inside the Vault, pressing system back locks and returns to Calculator
    BackHandler(enabled = screenState != ScreenState.CALCULATOR) {
        screenState = ScreenState.CALCULATOR
    }

    Crossfade(targetState = screenState, label = "ScreenTransition") { currentScreen ->
        when (currentScreen) {
            ScreenState.CALCULATOR -> {
                SubnetCalculatorScreen(
                    userPrefs = userPrefs,
                    onOpenVaultRequested = {
                        screenState = ScreenState.VAULT_LOCK
                    }
                )
            }
            ScreenState.VAULT_LOCK -> {
                VaultLockScreen(
                    userPrefs = userPrefs,
                    onUnlocked = {
                        screenState = ScreenState.VAULT_MAIN
                    },
                    onCancel = {
                        screenState = ScreenState.CALCULATOR
                    }
                )
            }
            ScreenState.VAULT_MAIN -> {
                VaultMainScreen(
                    vaultRepo = vaultRepo,
                    userPrefs = userPrefs,
                    cloudClient = cloudClient,
                    onLockVault = {
                        screenState = ScreenState.CALCULATOR
                    }
                )
            }
        }
    }
}
