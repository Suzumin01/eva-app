# EVA Frontend

Android-приложение платформы ЕВА (Kotlin + Jetpack Compose).

## Требования

- Android Studio Hedgehog или новее
- Android SDK 26+ (minSdk 26)
- Эмулятор или устройство Android
- Запущенный бэкенд (eva-backend) на порту 8081

## Конфигурация

URL бэкенда задаётся в `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081/api/v1/\"")
```

`10.0.2.2` — localhost хост-машины из эмулятора Android. При использовании реального устройства замените на IP хоста в локальной сети.

## Запуск

```bash
# Сборка debug APK
./gradlew assembleDebug

# Установить на подключённое устройство/эмулятор
./gradlew installDebug
```

Или откройте проект в Android Studio и нажмите Run.

## Экраны

| Экран | Путь навигации | Описание |
|-------|---------------|----------|
| Onboarding | `/onboarding` | Вводный экран (при отсутствии токена) |
| Consent | `/consent` | Согласия ФЗ-152 и AI |
| Login / Register | `/login`, `/register` | Аутентификация |
| Home | `/home` | Главный экран (ближайшая запись, быстрые действия) |
| Doctors | `/doctors` | Список врачей с фильтрами |
| Doctor Detail | `/doctor/{id}` | Профиль врача, расписание, отзывы |
| Appointments | `/appointments` | Мои записи |
| Symptoms | `/symptoms` | AI-анализ симптомов |
| Profile | `/profile` | Профиль (фото, выход) |
| Settings | `/settings` | Редактирование профиля, медкарта |
| Notifications | `/notifications` | Уведомления |
| Documents | `/documents` | Медицинские документы |
| Health Setup | `/health-setup` | Заполнение медданных (аллергии, хронические болезни) |

## Архитектура

MVVM + Hilt DI:

```
data/
  api/EvaApi.kt         ← Retrofit интерфейс (50+ эндпоинтов)
  api/Dto.kt            ← DTO для API
  local/TokenManager.kt ← DataStore (JWT, refresh-токен, настройки)
  local/room/           ← Room DB (кэш врачей)
  repository/           ← Репозитории (API + Room)
di/NetworkModule.kt     ← Hilt: OkHttp (JWT interceptor + authenticator), Retrofit
presentation/
  {feature}/            ← ViewModel + Screen по фичам
  navigation/Screen.kt  ← Все маршруты навигации
MainActivity.kt         ← NavController, bottom navigation, FCM deep links
```

## Ключевые зависимости

- **Jetpack Compose** — декларативный UI
- **Hilt** — dependency injection
- **Retrofit + OkHttp** — HTTP-клиент с автоматическим обновлением JWT
- **Room** — локальный кэш врачей
- **Coil** — загрузка изображений (фото врачей, аватар пользователя)
- **DataStore** — хранение токенов и настроек
- **Firebase Messaging** — push-уведомления
- **Dark theme** — полная поддержка тёмной темы с M3 80-tone цветами
- **Slide transitions** — анимации горизонтального слайда между экранами
- **Avatar/initials** — фото пользователя и врача везде; инициалы с цветом по хешу имени как fallback

## Тестирование

Инструментальные тесты Espresso/Compose (12 тестов: T01–T06 чистый UI, T07–T12 E2E с бэкендом):

```bash
# Требуется запущенный эмулятор и бэкенд (для T07–T12)
$env:JAVA_HOME="C:\Users\nikit\.jdks\ms-17.0.15"; .\gradlew connectedAndroidTest
# Результат: 12/12 passed ✅
```

Тестовый аккаунт: `testeva@mail.ru` / `Test1234!` (должен быть зарегистрирован в БД).

Файлы тестов:
- `app/src/androidTest/java/com/eva/app/EvaUiTest.kt` — 12 тест-кейсов
- `app/src/androidTest/java/com/eva/app/HiltTestRunner.kt` — Hilt-совместимый test runner

## Push-уведомления

FCM-токен регистрируется при входе (`POST /auth/fcm-token`).  
Типы уведомлений: `appointment_created`, `appointment_cancelled`, `appointment_reminder_24h`, `appointment_reminder_1h`.  
Deep link из уведомления открывает экран `/notification/{notifId}`.
