package com.eva.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

//
// Единый стандарт шрифтов для всего приложения.
// Размеры — M3 дефолты; кастомизируются только fontWeight и lineHeight.
//
// Использование: Text(style = EvaType.cardTitle, ...)
//
object EvaType {

    /** Имя пользователя / врача в gradient-шапке. headlineSmall (24sp) Bold. */
    val heroTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold, lineHeight = 32.sp)

    /** Приветствие «Доброе утро». bodyLarge (16sp). */
    val heroGreeting: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge

    /** Подзаголовок в шапке: специализация. bodyMedium (14sp). */
    val heroSub: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    /** Мелкий текст в шапке: клиника, адрес. bodySmall (12sp). */
    val heroCaption: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Числовое значение стата в шапке. titleLarge (22sp) Bold. */
    val heroStat: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold, lineHeight = 26.sp)

    /** Подпись стата: «лет опыта», «рейтинг». labelSmall (11sp). */
    val heroStatLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    /** Заголовок раздела внутри экрана: «О враче», «Клиника», настройки. titleSmall (14sp) SemiBold. */
    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold, lineHeight = 26.sp)

    /** Основной заголовок карточки/строки: имя врача, клиника, специализация. titleMedium (16sp) Bold. */
    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold)

    /** Вторичный текст карточки: специализация, дата. bodyMedium (14sp). */
    val cardSub: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    /** Третичный текст карточки: адрес, время, иконочные подписи. bodySmall (12sp). */
    val cardMeta: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Заголовок в теле экрана: уведомление, результат AI. titleLarge (22sp) Bold. */
    val screenTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold)

    /** Заголовок BottomSheet и AlertDialog. titleMedium (16sp) Bold. */
    val sheetTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold)

    /** Основной текст тела: биография, описание, тело уведомления, рекомендации AI. bodyLarge (16sp). */
    val bodyText: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)

    /** Метка карточки-меню в сетке главного экрана. labelMedium (12sp) Medium. */
    val menuLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium)
}

object EvaGradients {
    val doctors      = listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
    val clinics      = listOf(Color(0xFF00838F), Color(0xFF4DD0E1))
    val specs        = listOf(Color(0xFF6A1B9A), Color(0xFFAB47BC))
    val ai           = listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))
    val appointments = listOf(Color(0xFFE65100), Color(0xFFFF8A65))
    val documents    = listOf(Color(0xFF37474F), Color(0xFF78909C))
    val notifications = listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
    val danger       = listOf(Color(0xFFB71C1C), Color(0xFFEF5350))
    val settings     = listOf(Color(0xFF37474F), Color(0xFF78909C))
}

// isDark: true если текущая схема тёмная (background почти чёрный, red < 0.5)
val ColorScheme.isDark: Boolean get() = background.red < 0.5f

// evaPurple: фиолетовый, адаптированный к теме (Purple 800 в светлой / Purple 200 в тёмной)
val ColorScheme.evaPurple: Color get() =
    if (isDark) Color(0xFFCE93D8) else Color(0xFF6A1B9A)

@Composable
fun urgencyColor(urgency: String?): Color = when (urgency) {
    "emergency", "urgent" -> MaterialTheme.colorScheme.error
    "normal"              -> MaterialTheme.colorScheme.primary
    else                  -> MaterialTheme.colorScheme.tertiary
}

// Заменяет паттерн Surface(primaryContainer) { Icon(primary) } везде

@Composable
fun GradientIconBox(
    icon: ImageVector,
    gradient: List<Color>,
    size: Dp = 44.dp,
    iconSize: Dp = (size.value * 0.5f).dp,
    contentDescription: String? = null
) {
    Box(
        modifier         = Modifier
            .size(size)
            .clip(RoundedCornerShape(size.value.toInt() / 3))
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = Color.White,
            modifier           = Modifier.size(iconSize)
        )
    }
}

// Плоская иконка без фона, с цветом (для списков)

@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    Box(
        modifier         = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size((size.value * 0.55f).dp)
        )
    }
}

// Заменяет AssistChip/FilterChip для статусов (без border, лёгкий фон)

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text  = text,
            color = color,
            style = EvaType.cardMeta,
            fontWeight = FontWeight.Medium
        )
    }
}

// Заголовок секции без Card-обёртки

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text     = title,
        style    = EvaType.sectionTitle,
        color    = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

// Divider с отступом слева (под иконку)

@Composable
fun ListDivider(startIndent: Dp = 68.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = startIndent))
}

// Универсальная строка списка: иконка-бокс + заголовок + подзаголовок + слот справа

@Composable
fun RowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
    end: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon = icon, size = iconSize)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                style      = EvaType.cardTitle)
            if (subtitle != null) {
                Text(subtitle,
                    style = EvaType.cardMeta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        end()
    }
}
