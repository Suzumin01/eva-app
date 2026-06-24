<div align="center">
  <h1>EVA Android</h1>
  <p>Мобильное приложение платформы ЕВА — Единый Врачебный Ассистент</p>

  ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
  ![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)
  ![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)
</div>

---

## Возможности

- Запись к врачу: выбор специализации, клиники, врача и слота
- AI-анализ симптомов с историей и квотой
- Push-уведомления FCM с deep link в нотификацию
- Медицинские документы: загрузка PDF и изображений
- Отзывы врачам (только после завершённого приёма)
- Фото профиля, медицинская карта
- Полная поддержка тёмной темы (M3)
- Slide-анимации между экранами, initials-аватар как fallback

## Быстрый старт

**Требования:** Android Studio, Android SDK 26+, запущенный [eva-backend](../eva-backend) на порту 8081.

```bash
./gradlew assembleDebug    # сборка APK
./gradlew installDebug     # установка на эмулятор/устройство
```

URL бэкенда задан в `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081/api/v1/\"")
```
`10.0.2.2` — localhost хост-машины из Android-эмулятора. На реальном устройстве замените на IP в локальной сети.

## Экраны

| Экран | Описание |
|-------|----------|
| Onboarding | Вводный экран (при первом запуске) |
| Consent | Согласия ФЗ-152 и AI |
| Login / Register | Аутентификация |
| Home | Главный экран: ближайшая запись, быстрые действия |
| Doctors | Список врачей с фильтрами и поиском |
| Doctor Detail | Профиль врача, расписание, отзывы |
| Booking | Выбор даты и слота, подтверждение |
| Appointments | Мои записи |
| Symptoms | AI-анализ симптомов |
| Profile / Settings | Профиль, медкарта, редактирование |
| Notifications | Лента уведомлений |
| Documents | Медицинские документы |

## Архитектура

MVVM + Hilt DI:

```
data/
  api/EvaApi.kt         ← Retrofit интерфейс (50+ эндпоинтов)
  local/TokenManager.kt ← DataStore (JWT, refresh-токен, настройки)
  local/room/           ← Room DB (локальный кэш врачей)
  repository/           ← Репозитории (API + Room)
di/NetworkModule.kt     ← Hilt: OkHttp (JWT interceptor + authenticator), Retrofit
presentation/
  {feature}/            ← ViewModel + Screen по фичам
  navigation/Screen.kt  ← все маршруты навигации
  components/           ← DesignSystem, переиспользуемые компоненты
MainActivity.kt         ← NavController, bottom navigation, FCM deep links
```

**Автообновление токена:** OkHttp `Authenticator` перехватывает 401, вызывает `POST /auth/refresh` и повторяет оригинальный запрос. `navigate()` из FCM-коллектора выполняется через `withContext(Dispatchers.Main)` — OkHttp стреляет с background-треда.

## Тестирование

13 инструментальных тестов Espresso + Hilt (T01–T06 — чистый UI, T07–T13 — E2E с бэкендом):

```bash
./gradlew connectedAndroidTest
# 13/13 passed ✅
```

Тестовый аккаунт: `testeva@mail.ru` / `Test1234!` (должен быть в БД).
