package com.eva.app

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.eva.app.data.local.TokenManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * E2E UI tests for the ЕВА app.
 *
 * T01–T06 — pure UI tests, no network required.
 * T07–T13 — integration tests, require:
 *   • Backend running at 10.0.2.2:8081
 *   • A pre-registered account: TEST_EMAIL / TEST_PASSWORD
 *   • Emulator/device connected
 *
 * Run from terminal:  ./gradlew connectedAndroidTest
 */

private const val TEST_EMAIL    = "testeva@mail.ru"
private const val TEST_PASSWORD = "Test1234!"

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EvaUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val grantPermissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        hiltRule.inject()
        // Wait for app to be fully composed before touching state.
        composeTestRule.waitForIdle()
        // clearAuth() wipes DataStore + in-memory cachedToken — must run on main thread
        // so DataStore lifecycle callbacks fire correctly.
        composeTestRule.runOnUiThread {
            runBlocking { tokenManager.clearAuth() }
            // emitUnauthorized() uses the app's own "401 handler" to navigate to
            // Login from any screen, clearing the entire back stack.
            tokenManager.emitUnauthorized()
        }
        // Wait until Login screen is visible before each test.
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("Вход в аккаунт")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typeEmail(email: String) =
        composeTestRule.onNode(hasText("Email") and hasSetTextAction())
            .performTextInput(email)

    private fun typePassword(password: String) =
        composeTestRule.onNode(hasText("Пароль") and hasSetTextAction())
            .performTextInput(password)

    private fun loginWith(email: String, password: String) {
        typeEmail(email)
        typePassword(password)
        composeTestRule.onNodeWithText("Войти").performClick()
    }

    private fun loginAndReachHome() {
        loginWith(TEST_EMAIL, TEST_PASSWORD)
        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Соглашения").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty()
        }
        // Consent screen shown on first login — skip it
        if (composeTestRule.onAllNodesWithText("Соглашения").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Пропустить — решу позже").performClick()
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty()
            }
        }
        // Health setup screen shown after consent — skip it
        if (composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Пропустить — заполню позже").performClick()
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun t01_loginScreen_elementsDisplayed() {
        composeTestRule.onNodeWithText("Вход в аккаунт").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Пароль").assertIsDisplayed()
        composeTestRule.onNodeWithText("Войти").assertIsDisplayed()
        composeTestRule.onNodeWithText("Нет аккаунта? Зарегистрироваться").assertIsDisplayed()
        composeTestRule.onNodeWithText("Забыли пароль?").assertIsDisplayed()
    }

    @Test
    fun t02_loginButtonDisabledWhenFieldsEmpty() {
        composeTestRule.onNodeWithText("Войти").assertIsNotEnabled()
    }

    @Test
    fun t03_loginButtonEnabledWhenBothFieldsFilled() {
        typeEmail("user@example.com")
        typePassword("somepassword")
        composeTestRule.onNodeWithText("Войти").assertIsEnabled()
    }

    @Test
    fun t04_navigateToRegister() {
        composeTestRule.onNodeWithText("Нет аккаунта? Зарегистрироваться").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Создайте аккаунт ЕВА").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("ФИО").assertIsDisplayed()
        composeTestRule.onNodeWithText("Создать аккаунт").assertIsDisplayed()
    }

    @Test
    fun t05_registerPasswordMismatch_showsError() {
        composeTestRule.onNodeWithText("Нет аккаунта? Зарегистрироваться").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Пароль (мин. 8 символов)")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasText("Пароль (мин. 8 символов)") and hasSetTextAction())
            .performTextInput("password123")
        composeTestRule.onNode(hasText("Повторите пароль") and hasSetTextAction())
            .performTextInput("differentpass")
        composeTestRule.onNodeWithText("Пароли не совпадают").assertIsDisplayed()
    }

    @Test
    fun t06_navigateToForgotPassword() {
        composeTestRule.onNodeWithText("Забыли пароль?").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Отправить").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отправить").assertIsDisplayed()
    }

    @Test
    fun t07_wrongCredentials_errorShown() {
        typeEmail("nobody@nowhere.invalid")
        typePassword("wrongpass999")
        composeTestRule.onNodeWithText("Войти").performClick()
        composeTestRule.waitUntil(12_000) {
            composeTestRule.onAllNodes(
                hasText("Неверный", substring = true) or
                hasText("не найден", substring = true) or
                hasText("Нет соединения", substring = true)
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── T08: Valid login reaches Home, Consent, or Health Setup screen [NETWORK]
    @Test
    fun t08_validLogin_homeConsentOrHealthShown() {
        loginWith(TEST_EMAIL, TEST_PASSWORD)
        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Соглашения").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun t09_homeScreen_showsNavigationCards() {
        loginAndReachHome()
        composeTestRule.onNodeWithText("Поиск врача").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI-анализ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Мои записи").assertIsDisplayed()
    }

    @Test
    fun t10_bottomNavigation_switchesTabs() {
        loginAndReachHome()

        // Записи tab
        composeTestRule.onAllNodesWithText("Записи").onFirst().performClick()
        composeTestRule.waitUntil(8_000) {
            composeTestRule.onAllNodes(hasText("Предстоящие", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Вернуться на Главная — иначе popUpTo(Home) не найдёт Home в стеке
        // и клик на Симптомы может не сработать
        composeTestRule.onAllNodesWithText("Главная").onFirst().performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Поиск врача").fetchSemanticsNodes().isNotEmpty()
        }

        // Симптомы tab
        composeTestRule.onAllNodesWithText("Симптомы").onFirst().performClick()
        composeTestRule.waitUntil(8_000) {
            composeTestRule.onAllNodesWithText("История запросов").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Новый запрос").fetchSemanticsNodes().isNotEmpty()
        }

        // Профиль tab
        composeTestRule.onAllNodesWithText("Профиль").onFirst().performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Медицинская карта").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun t11_appointmentsScreen_showsBothTabs() {
        loginAndReachHome()
        composeTestRule.onAllNodesWithText("Записи").onFirst().performClick()
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasText("Предстоящие", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodes(hasText("Предстоящие", substring = true))
            .onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("Прошедшие", substring = true))
            .onFirst().assertIsDisplayed()
    }

    @Test
    fun t13_healthSetupScreen_showsFieldsAndSkip() {
        loginWith(TEST_EMAIL, TEST_PASSWORD)
        composeTestRule.waitUntil(15_000) {
            composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Соглашения").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeTestRule.onAllNodesWithText("Соглашения").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Пропустить — решу позже").performClick()
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty() ||
                composeTestRule.onAllNodesWithText("Главная").fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (composeTestRule.onAllNodesWithText("Ваш профиль здоровья").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Ваш профиль здоровья").assertIsDisplayed()
            composeTestRule.onNodeWithText("Аллергии").assertIsDisplayed()
            composeTestRule.onNodeWithText("Хронические заболевания").assertIsDisplayed()
            composeTestRule.onNodeWithText("Пропустить — заполню позже").assertIsDisplayed()
        }
    }

    // ── T12: Profile screen shows Medical Card and Settings menu items [NETWORK]
    @Test
    fun t12_profileScreen_showsMenuItems() {
        loginAndReachHome()
        composeTestRule.onAllNodesWithText("Профиль").onFirst().performClick()
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText("Медицинская карта").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Медицинская карта").assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("Настройки", substring = true))
            .onFirst().assertIsDisplayed()
    }
}
