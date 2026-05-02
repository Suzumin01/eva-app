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
// Основа: размеры Главного экрана (HomeScreen).
//
// Использование: Text(style = EvaType.cardTitle, ...)
//
// Иерархия (сверху вниз — от крупного к мелкому):
//   heroTitle 24 sp Bold  →  sectionTitle 18 sp SemiBold  →  cardTitle 16 sp Bold
//   →  bodyText 15 sp  →  cardSub 13 sp  →  cardMeta 12 sp
//
object EvaType {

    /** Имя пользователя / врача в gradient-шапке. 24 sp Bold. */
    val heroTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold, lineHeight = 30.sp)

    /** Приветствие «Доброе утро». 17 sp. */
    val heroGreeting: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)

    /** Подзаголовок в шапке: специализация. 13 sp. */
    val heroSub: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)

    /** Мелкий текст в шапке: клиника, адрес. 12 sp. */
    val heroCaption: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Числовое значение стата в шапке. 20 sp Bold. */
    val heroStat: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)

    /** Подпись стата: «лет опыта», «рейтинг». 11 sp. */
    val heroStatLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    /**
     * Заголовок раздела внутри экрана: «О враче», «Клиника», настроечные секции.
     * 18 sp SemiBold — крупнее основного текста, меньше hero.
     */
    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmall.copy(
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)

    /** Основной заголовок карточки/строки: имя врача, клиника, специализация. 16 sp Bold. */
    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

    /** Вторичный текст карточки: специализация, дата. 13 sp. */
    val cardSub: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)

    /** Третичный текст карточки: адрес, время, иконочные подписи. 12 sp. */
    val cardMeta: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Заголовок в теле экрана: уведомление, результат AI. 22 sp Bold. */
    val screenTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    /** Заголовок BottomSheet и AlertDialog. 18 sp Bold. */
    val sheetTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp, fontWeight = FontWeight.Bold)

    /**
     * Основной текст тела: биография, описание, тело уведомления, рекомендации AI.
     * 15 sp — чуть меньше cardTitle (16 sp), чёткая ступень под заголовком секции.
     */
    val bodyText: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp, lineHeight = 22.sp)

    /** Метка карточки-меню в сетке главного экрана. 13 sp Medium. */
    val menuLabel: TextStyle
        @Composable get() = MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

fun urgencyColor(urgency: String?) = when (urgency) {
    "emergency", "urgent" -> Color(0xFFB71C1C)
    "normal"              -> Color(0xFF1565C0)
    else                  -> Color(0xFF2E7D32)
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
            style = MaterialTheme.typography.labelSmall,
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
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        end()
    }
}
