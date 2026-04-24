package com.eva.app.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eva.app.R
import com.eva.app.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val gradientStart: Color,
    val gradientEnd: Color
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch { tokenManager.setOnboardingDone(); onDone() }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pages = listOf(
        OnboardingPage(
            icon          = Icons.Default.HealthAndSafety,
            title         = stringResource(R.string.onboarding_page1_title),
            description   = stringResource(R.string.onboarding_page1_desc),
            gradientStart = Color(0xFF0D47A1),
            gradientEnd   = Color(0xFF1976D2)
        ),
        OnboardingPage(
            icon          = Icons.Default.CalendarMonth,
            title         = stringResource(R.string.onboarding_page2_title),
            description   = stringResource(R.string.onboarding_page2_desc),
            gradientStart = Color(0xFF006064),
            gradientEnd   = Color(0xFF00838F)
        ),
        OnboardingPage(
            icon          = Icons.Default.Psychology,
            title         = stringResource(R.string.onboarding_page3_title),
            description   = stringResource(R.string.onboarding_page3_desc),
            gradientStart = Color(0xFF1B5E20),
            gradientEnd   = Color(0xFF2E7D32)
        ),
        OnboardingPage(
            icon          = Icons.Default.Notifications,
            title         = stringResource(R.string.onboarding_page4_title),
            description   = stringResource(R.string.onboarding_page4_desc),
            gradientStart = Color(0xFF4A148C),
            gradientEnd   = Color(0xFF7B1FA2)
        )
    )

    val pagerState = rememberPagerState { pages.size }
    val scope      = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { idx ->
            val page = pages[idx]
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(page.gradientStart, page.gradientEnd))
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Surface(
                        shape    = RoundedCornerShape(32.dp),
                        color    = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(page.icon, null, tint = Color.White,
                                modifier = Modifier.size(72.dp))
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(page.title, color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(page.description, color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center, lineHeight = 24.sp)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pages.indices.forEach { idx ->
                val isSelected = pagerState.currentPage == idx
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                    label = "dot"
                )
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }

        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            if (pagerState.currentPage < pages.size - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { viewModel.finish(onDone) }) {
                        Text(stringResource(R.string.btn_skip), color = Color.White.copy(alpha = 0.7f))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape  = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.btn_next),
                            color = pages[pagerState.currentPage].gradientStart,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null,
                            tint     = pages[pagerState.currentPage].gradientStart,
                            modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Button(
                    onClick  = { viewModel.finish(onDone) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null,
                        tint = pages.last().gradientStart)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_start),
                        color = pages.last().gradientStart,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
