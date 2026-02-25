package com.eva.app.util

object ErrorMapper {
    fun map(rawMessage: String, httpCode: Int? = null): String {
        val lower = rawMessage.lowercase()
        return when {
            lower.contains("invalid credentials") || lower.contains("wrong password") ||
            lower.contains("неверный") -> "Неверный email или пароль"
            lower.contains("user not found") || lower.contains("not found") && lower.contains("user") ->
                "Пользователь с таким email не найден"
            lower.contains("email already") || lower.contains("already exists") || lower.contains("duplicate") ->
                "Этот email уже зарегистрирован. Попробуйте войти."
            lower.contains("password") && (lower.contains("short") || lower.contains("weak") || lower.contains("length")) ->
                "Пароль должен содержать не менее 8 символов"
            lower.contains("invalid email") || lower.contains("email format") ->
                "Введите корректный адрес электронной почты"
            lower.contains("token") && (lower.contains("expired") || lower.contains("invalid")) ->
                "Сессия истекла. Пожалуйста, войдите снова."
            lower.contains("unauthorized") || httpCode == 401 ->
                "Необходима авторизация"
            lower.contains("forbidden") || httpCode == 403 ->
                "Нет доступа к этому разделу"
            lower.contains("slot") && (lower.contains("unavailable") || lower.contains("taken") || lower.contains("занят")) ->
                "Этот слот уже занят. Выберите другое время."
            lower.contains("schedule") && lower.contains("not found") ->
                "Слот расписания не найден. Обновите страницу."
            lower.contains("connection") || lower.contains("timeout") || lower.contains("host") ||
            lower.contains("socket") || lower.contains("сервер") ->
                "Нет соединения с сервером. Проверьте подключение к интернету."
            lower.contains("500") || httpCode == 500 ->
                "Ошибка на сервере. Попробуйте позже."
            lower.contains("404") || httpCode == 404 ->
                "Данные не найдены"
            lower.contains("400") || httpCode == 400 ->
                "Проверьте правильность введённых данных"
            else -> rawMessage.take(120)
        }
    }
}
