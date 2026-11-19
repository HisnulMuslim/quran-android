package com.quran.labs.androidquran.util

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

private data class InitialLanguageOption(
  val code: String,
  val primaryTitle: String,
  val subtitle: String,
  val badge: String,
  val themeTitle: String,
  val darkLabel: String,
  val lightLabel: String,
  val autoLabel: String,
  val note: String,
  val ctaLabel: String
)

private val INITIAL_LANGUAGE_OPTIONS = listOf(
  InitialLanguageOption(
    code = "kn",
    primaryTitle = "ಕನ್ನಡ",
    subtitle = "Kannada",
    badge = "ಅ ಆ ಇ",
    themeTitle = "ಆ್ಯಪ್ ಥೀಮ್",
    darkLabel = "ಡಾರ್ಕ್",
    lightLabel = "ಲೈಟ್",
    autoLabel = "ಸಿಸ್ಟಮ್",
    note = "ಭಾಷೆ ಮತ್ತು ಥೀಮ್ ಅನ್ನು ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಯಾವಾಗ ಬೇಕಾದರೂ ಬದಲಾಯಿಸಬಹುದು",
    ctaLabel = "ಪ್ರಾರಂಭಿಸಿ"
  ),
  InitialLanguageOption(
    code = "en",
    primaryTitle = "English",
    subtitle = "English",
    badge = "A B C",
    themeTitle = "App Theme",
    darkLabel = "Dark",
    lightLabel = "Light",
    autoLabel = "System",
    note = "You can change language and theme anytime in Settings",
    ctaLabel = "Get Started"
  ),
  InitialLanguageOption(
    code = "ar",
    primaryTitle = "العربية",
    subtitle = "Arabic",
    badge = "ا ب ت",
    themeTitle = "مظهر التطبيق",
    darkLabel = "داكن",
    lightLabel = "فاتح",
    autoLabel = "تلقائي",
    note = "يمكنك تغيير اللغة والمظهر في أي وقت من الإعدادات",
    ctaLabel = "ابدأ"
  )
)

/**
 * Presents a one-time onboarding bottom sheet prompting the user to select their
 * preferred interface language (`kn`, `en`, or `ar`) and app theme (`dark`, `light`, or `auto`).
 */
object InitialLanguagePrompt {

  fun show(
    activity: AppCompatActivity,
    settings: QuranSettings,
    onConfirmed: (languageCode: String, appTheme: String) -> Unit
  ): BottomSheetDialog {
    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
    val initialTheme = prefs.getString(com.quran.labs.androidquran.data.Constants.PREF_APP_THEME, "dark") ?: "dark"
    val dialog = object : BottomSheetDialog(activity) {
      override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.decorView?.let { decor ->
          decor.setViewTreeLifecycleOwner(activity)
          decor.setViewTreeSavedStateRegistryOwner(activity)
          decor.setViewTreeViewModelStoreOwner(activity)
        }
      }
    }
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)

    val container = FrameLayout(activity).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }

    val composeView = ComposeView(activity).apply {
      setViewTreeLifecycleOwner(activity)
      setViewTreeSavedStateRegistryOwner(activity)
      setViewTreeViewModelStoreOwner(activity)
      setContent {
        MaterialTheme {
          InitialLanguageSheetContent(
            initialCode = settings.interfaceLanguage().lowercase(),
            initialTheme = initialTheme,
            onConfirm = { chosenCode, chosenTheme ->
              dialog.dismiss()
              onConfirmed(chosenCode, chosenTheme)
            }
          )
        }
      }
    }
    container.addView(composeView)
    dialog.setContentView(container)

    dialog.setOnShowListener {
      val bottomSheet = dialog.findViewById<FrameLayout>(
        com.google.android.material.R.id.design_bottom_sheet
      )
      bottomSheet?.let { sheet ->
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        behavior.isDraggable = false
      }
    }

    dialog.show()
    return dialog
  }
}

@Composable
private fun InitialLanguageSheetContent(
  initialCode: String,
  initialTheme: String,
  onConfirm: (String, String) -> Unit
) {
  val validInitial = INITIAL_LANGUAGE_OPTIONS.firstOrNull { it.code == initialCode }?.code ?: "kn"
  var selectedCode by remember { mutableStateOf(validInitial) }
  val validTheme = if (initialTheme in listOf("dark", "light", "auto")) initialTheme else "dark"
  var selectedTheme by remember { mutableStateOf(validTheme) }
  val activeOption = INITIAL_LANGUAGE_OPTIONS.first { it.code == selectedCode }

  val systemInDark = isSystemInDarkTheme()
  val isDark = when (selectedTheme) {
    "dark" -> true
    "light" -> false
    else -> systemInDark
  }

  val deepGreenBase = Color(0xFF022B24)
  val deepGreenTop = Color(0xFF083A30)
  val champagneGold = Color(0xFFDAB470)
  val accent = if (isDark) Color(0xFF22C59E) else deepGreenBase
  val sheetBg = if (isDark) Color(0xFF141817) else Color(0xFFFFFFFF)
  val cardBg = if (isDark) Color(0xFF1E2422) else Color(0xFFF6F7F6)
  val cardSelectedBg = if (isDark) Color(0xFF22C59E).copy(alpha = 0.12f) else deepGreenBase.copy(alpha = 0.06f)
  val badgeBg = if (isDark) Color(0xFF2C3431) else Color(0xFFE9ECEB)
  val badgeSelectedBg = if (isDark) Color(0xFF22C59E).copy(alpha = 0.20f) else deepGreenBase.copy(alpha = 0.10f)
  val primaryText = if (isDark) Color(0xFFF4F6F5) else Color(0xFF111816)
  val secondaryText = if (isDark) Color(0xFFA7B3AF) else Color(0xFF5F6B66)

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    color = sheetBg
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Grabber pill
      Box(
        modifier = Modifier
          .width(38.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(secondaryText.copy(alpha = 0.3f))
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Luxury Header Medallion
      Box(
        modifier = Modifier
          .size(58.dp)
          .border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
              colors = listOf(champagneGold.copy(alpha = 0.6f), accent.copy(alpha = 0.3f))
            ),
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(badgeSelectedBg),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Trilingual-friendly Title & Subtitle so every user recognizes their language immediately
      Text(
        text = "Choose App Language",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = primaryText,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "ಆ್ಯಪ್ ಭಾಷೆಯನ್ನು ಆಯ್ಕೆಮಾಡಿ  •  اختر لغة التطبيق",
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = secondaryText,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(16.dp))

      // 3 Luxury Language Cards
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        INITIAL_LANGUAGE_OPTIONS.forEach { option ->
          val isSelected = selectedCode == option.code
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(if (isSelected) cardSelectedBg else cardBg)
              .border(
                width = if (isSelected) 1.75.dp else 1.dp,
                color = if (isSelected) accent else primaryText.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
              )
              .clickable { selectedCode = option.code }
              .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .width(50.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) badgeSelectedBg else badgeBg)
                .border(
                  width = if (isSelected) 1.dp else 0.dp,
                  color = if (isSelected) accent.copy(alpha = 0.3f) else Color.Transparent,
                  shape = RoundedCornerShape(10.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = option.badge,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accent else secondaryText
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = option.primaryTitle,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = primaryText
              )
              if (option.subtitle != option.primaryTitle) {
                Text(
                  text = option.subtitle,
                  fontSize = 12.sp,
                  color = secondaryText
                )
              }
            }

            // Radio / Checkmark Indicator
            Box(
              modifier = Modifier.size(24.dp),
              contentAlignment = Alignment.Center
            ) {
              if (isSelected) {
                Box(
                  modifier = Modifier
                    .size(24.dp)
                    .border(2.5.dp, accent.copy(alpha = 0.25f), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Box(
                    modifier = Modifier
                      .size(20.dp)
                      .clip(CircleShape)
                      .background(accent),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(12.dp)
                    )
                  }
                }
              } else {
                Box(
                  modifier = Modifier
                    .size(21.dp)
                    .border(1.5.dp, secondaryText.copy(alpha = 0.35f), CircleShape)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // App Theme selector (Dark selected by default, lets user preview & switch right on first install)
      Text(
        text = "${activeOption.themeTitle}  •  App Theme",
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = secondaryText,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 2.dp, vertical = 4.dp)
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(cardBg)
          .border(1.dp, primaryText.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val themeChoices = listOf(
          Triple("dark", activeOption.darkLabel, Icons.Default.DarkMode),
          Triple("light", activeOption.lightLabel, Icons.Default.LightMode),
          Triple("auto", activeOption.autoLabel, Icons.Default.BrightnessAuto)
        )
        themeChoices.forEach { (themeKey, label, icon) ->
          val isThemeSelected = selectedTheme == themeKey
          Row(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isThemeSelected) accent else Color.Transparent)
              .clickable { selectedTheme = themeKey }
              .padding(vertical = 9.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (isThemeSelected) Color.White else secondaryText,
              modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = label,
              fontSize = 12.5.sp,
              fontWeight = if (isThemeSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isThemeSelected) Color.White else primaryText,
              maxLines = 1
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Settings hint note
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Info,
          contentDescription = null,
          tint = secondaryText.copy(alpha = 0.8f),
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = activeOption.note,
          fontSize = 12.sp,
          color = secondaryText,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Primary Emerald Gradient CTA Button
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            ambientColor = deepGreenBase.copy(alpha = 0.35f),
            spotColor = deepGreenBase.copy(alpha = 0.35f)
          )
          .clip(RoundedCornerShape(16.dp))
          .background(
            brush = Brush.verticalGradient(colors = listOf(deepGreenTop, deepGreenBase))
          )
          .border(
            border = BorderStroke(
              width = 1.dp,
              color = if (isDark) Color(0xFF22C59E).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
            ),
            shape = RoundedCornerShape(16.dp)
          )
          .clickable { onConfirm(selectedCode, selectedTheme) },
        contentAlignment = Alignment.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = activeOption.ctaLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}
