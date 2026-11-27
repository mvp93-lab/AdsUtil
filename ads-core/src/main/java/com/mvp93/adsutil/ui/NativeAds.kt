package com.mvp93.adsutil.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.utils.AdsConstants

private const val TAG_ROOT = "native_root"
private const val TAG_AD_BADGE = "native_ad_badge"
private const val TAG_MEDIA_VIEW = "media_view"
private const val TAG_CTA = "native_cta"

/**
 * Color palette used by [SmallNativeAd], [BigNativeAd], and their shimmer placeholders.
 */
@Immutable
data class NativeAdColors(
    val containerColor: Color,
    val headlineColor: Color,
    val bodyColor: Color,
    val badgeBackgroundColor: Color,
    val badgeContentColor: Color,
    val ctaBackgroundColor: Color,
    val ctaContentColor: Color
)

/**
 * Default color presets for Native Ads.
 */
object NativeAdDefaults {
    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
        headlineColor: Color = MaterialTheme.colorScheme.onSurface,
        bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        badgeBackgroundColor: Color = MaterialTheme.colorScheme.primary,
        badgeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        ctaBackgroundColor: Color = MaterialTheme.colorScheme.primary,
        ctaContentColor: Color = MaterialTheme.colorScheme.onPrimary
    ): NativeAdColors = NativeAdColors(
        containerColor = containerColor,
        headlineColor = headlineColor,
        bodyColor = bodyColor,
        badgeBackgroundColor = badgeBackgroundColor,
        badgeContentColor = badgeContentColor,
        ctaBackgroundColor = ctaBackgroundColor,
        ctaContentColor = ctaContentColor
    )

    fun darkColors(): NativeAdColors = NativeAdColors(
        containerColor = Color(0xFF1A1C1E),
        headlineColor = Color.White,
        bodyColor = Color.Gray,
        badgeBackgroundColor = Color(0xFF1872E8),
        badgeContentColor = Color.White,
        ctaBackgroundColor = Color(0xFF1872E8),
        ctaContentColor = Color.White
    )
}

private class NativeAdState(
    val nativeAd: NativeAd?,
    val isLoading: Boolean
)

@Composable
private fun rememberNativeAdState(
    adUnitId: String,
    screenTag: String,
    isInitialized: Boolean
): NativeAdState {
    val context = LocalContext.current
    var nativeAd by remember(screenTag, adUnitId) {
        mutableStateOf(AdManager.getCachedNativeAd(screenTag, adUnitId))
    }
    var isLoading by remember(screenTag, adUnitId) { mutableStateOf(nativeAd == null) }

    LaunchedEffect(adUnitId, screenTag, isInitialized) {
        if (!isInitialized) return@LaunchedEffect
        val cached = AdManager.getCachedNativeAd(screenTag, adUnitId)
        if (cached != null) {
            nativeAd = cached
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        AdManager.loadNativeAd(
            context = context,
            adUnitId = adUnitId,
            screenTag = screenTag,
            onAdLoaded = {
                nativeAd = it
                isLoading = false
            },
            onAdFailed = {
                isLoading = false
            }
        )
    }

    return NativeAdState(nativeAd = nativeAd, isLoading = isLoading)
}

private fun bindNativeAdIfNeeded(
    adView: NativeAdView,
    ad: NativeAd,
    defaultCta: String,
    iconCornerRadius: Float,
    mediaView: MediaView?
) {
    // Avoid re-registering and reloading assets on unrelated Compose recompositions.
    if (adView.tag === ad) return
    adView.tag = ad

    (adView.headlineView as? TextView)?.text = ad.headline ?: "Sponsored"
    (adView.bodyView as? TextView)?.apply {
        val bodyText = ad.body
        if (bodyText.isNullOrBlank()) {
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            text = bodyText
        }
    }
    (adView.callToActionView as? TextView)?.text = ad.callToAction ?: defaultCta

    (adView.iconView as? ImageView)?.apply {
        val iconAsset = ad.icon
        if (iconAsset != null) {
            visibility = View.VISIBLE
            load(iconAsset.uri) {
                transformations(RoundedCornersTransformation(iconCornerRadius))
            }
        } else {
            setImageDrawable(null)
            visibility = View.GONE
        }
    }

    adView.registerNativeAd(ad, mediaView)
}

private fun applyNativeAdColors(
    adView: NativeAdView,
    colors: NativeAdColors,
    ctaCornerRadiusPx: Float
) {
    val density = adView.resources.displayMetrics.density
    adView.findViewWithTag<View>(TAG_ROOT)?.setBackgroundColor(colors.containerColor.toArgb())
    (adView.headlineView as? TextView)?.setTextColor(colors.headlineColor.toArgb())
    (adView.bodyView as? TextView)?.setTextColor(colors.bodyColor.toArgb())

    adView.findViewWithTag<TextView>(TAG_AD_BADGE)?.apply {
        setTextColor(colors.badgeContentColor.toArgb())
        background = GradientDrawable().apply {
            setColor(colors.badgeBackgroundColor.toArgb())
            cornerRadius = 4 * density
        }
    }

    adView.findViewWithTag<TextView>(TAG_CTA)?.apply {
        setTextColor(colors.ctaContentColor.toArgb())
        background = GradientDrawable().apply {
            setColor(colors.ctaBackgroundColor.toArgb())
            cornerRadius = ctaCornerRadiusPx
        }
    }
}

private fun createAdChoicesView(context: Context): AdChoicesView =
    AdChoicesView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
        }
    }

private fun createAdBadgeView(
    context: Context,
    horizontalPaddingDp: Int
): TextView {
    val density = context.resources.displayMetrics.density
    return TextView(context).apply {
        tag = TAG_AD_BADGE
        text = "Ad"
        textSize = 10f
        setPadding(
            (horizontalPaddingDp * density).toInt(),
            (2 * density).toInt(),
            (horizontalPaddingDp * density).toInt(),
            (2 * density).toInt()
        )
    }
}

@Composable
fun ShimmerLoading(
    modifier: Modifier = Modifier,
    height: Dp,
    cornerRadius: Dp = 12.dp,
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = lerp(baseColor, MaterialTheme.colorScheme.onSurface, 0.12f)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}

@Composable
fun SmallNativeAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsConstants.AD_NATIVE_TEST_ID,
    screenTag: String = "default",
    colors: NativeAdColors = NativeAdDefaults.colors(),
    shimmerHeight: Dp = 80.dp
) {
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()
    val isInitialized by AdManager.isInitialized.collectAsState()

    if (!isAdsEnabled) return

    val adState = rememberNativeAdState(
        adUnitId = adUnitId,
        screenTag = screenTag,
        isInitialized = isInitialized
    )

    if (adState.isLoading) {
        ShimmerLoading(
            modifier = modifier,
            height = shimmerHeight,
            cornerRadius = 12.dp,
            baseColor = colors.containerColor,
            highlightColor = lerp(colors.containerColor, colors.headlineColor, 0.12f)
        )
    } else {
        val ad = adState.nativeAd ?: return
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            factory = { context ->
                NativeAdView(context).apply {
                    val density = context.resources.displayMetrics.density

                    val adChoices = createAdChoicesView(context)
                    this.adChoicesView = adChoices
                    this.addView(adChoices)

                    val root = LinearLayout(context).apply {
                        tag = TAG_ROOT
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        val p = (16 * density).toInt()
                        setPadding(p, p, p, p)
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val icon = ImageView(context).apply {
                        val size = (48 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            rightMargin = (12 * density).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    this.iconView = icon
                    root.addView(icon)

                    val textContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val adBadge = createAdBadgeView(context, horizontalPaddingDp = 6).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = (2 * density).toInt()
                        }
                    }
                    textContainer.addView(adBadge)

                    val headline = TextView(context).apply {
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    this.headlineView = headline
                    textContainer.addView(headline)

                    val body = TextView(context).apply {
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    this.bodyView = body
                    textContainer.addView(body)
                    root.addView(textContainer)

                    val cta = TextView(context).apply {
                        tag = TAG_CTA
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            (36 * density).toInt()
                        ).apply {
                            leftMargin = (8 * density).toInt()
                        }
                        setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        isAllCaps = true
                    }
                    this.callToActionView = cta
                    root.addView(cta)

                    this.addView(root)
                }
            },
            update = { view ->
                val adView = view as NativeAdView
                val density = adView.resources.displayMetrics.density
                applyNativeAdColors(
                    adView = adView,
                    colors = colors,
                    ctaCornerRadiusPx = 8 * density
                )
                bindNativeAdIfNeeded(
                    adView = adView,
                    ad = ad,
                    defaultCta = "OPEN",
                    iconCornerRadius = 24f,
                    mediaView = null
                )
            }
        )
    }
}

@Composable
fun BigNativeAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsConstants.AD_NATIVE_TEST_ID,
    screenTag: String = "default",
    colors: NativeAdColors = NativeAdDefaults.colors(),
    shimmerHeight: Dp = 308.dp
) {
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()
    val isInitialized by AdManager.isInitialized.collectAsState()

    if (!isAdsEnabled) return

    val adState = rememberNativeAdState(
        adUnitId = adUnitId,
        screenTag = screenTag,
        isInitialized = isInitialized
    )

    if (adState.isLoading) {
        ShimmerLoading(
            modifier = modifier,
            height = shimmerHeight,
            cornerRadius = 16.dp,
            baseColor = colors.containerColor,
            highlightColor = lerp(colors.containerColor, colors.headlineColor, 0.12f)
        )
    } else {
        val ad = adState.nativeAd ?: return
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            factory = { context ->
                NativeAdView(context).apply {
                    val density = context.resources.displayMetrics.density

                    val adChoices = createAdChoicesView(context)
                    this.adChoicesView = adChoices
                    this.addView(adChoices)

                    val root = LinearLayout(context).apply {
                        tag = TAG_ROOT
                        orientation = LinearLayout.VERTICAL
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val mediaContainer = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (180 * density).toInt()
                        )
                    }

                    val media = MediaView(context).apply {
                        tag = TAG_MEDIA_VIEW
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    mediaContainer.addView(media)

                    val adBadge = createAdBadgeView(context, horizontalPaddingDp = 8).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            val margin = (12 * density).toInt()
                            setMargins(margin, margin, 0, 0)
                        }
                    }
                    mediaContainer.addView(adBadge)

                    root.addView(mediaContainer)

                    val bottomRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        val p = (12 * density).toInt()
                        setPadding(p, p, p, p)
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val icon = ImageView(context).apply {
                        val size = (44 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            rightMargin = (12 * density).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                    this.iconView = icon
                    bottomRow.addView(icon)

                    val textContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val headline = TextView(context).apply {
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    this.headlineView = headline
                    textContainer.addView(headline)

                    val body = TextView(context).apply {
                        textSize = 13f
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    this.bodyView = body
                    textContainer.addView(body)
                    bottomRow.addView(textContainer)
                    root.addView(bottomRow)

                    val cta = TextView(context).apply {
                        tag = TAG_CTA
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (48 * density).toInt()
                        ).apply {
                            val margin = (12 * density).toInt()
                            setMargins(margin, 0, margin, margin)
                        }
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.CENTER
                        isAllCaps = true
                    }
                    this.callToActionView = cta
                    root.addView(cta)

                    this.addView(root)
                }
            },
            update = { view ->
                val adView = view as NativeAdView
                val density = adView.resources.displayMetrics.density
                applyNativeAdColors(
                    adView = adView,
                    colors = colors,
                    ctaCornerRadiusPx = 12 * density
                )
                val mediaView = adView.findViewWithTag<MediaView>(TAG_MEDIA_VIEW)
                bindNativeAdIfNeeded(
                    adView = adView,
                    ad = ad,
                    defaultCta = "LEARN MORE",
                    iconCornerRadius = 50f,
                    mediaView = mediaView
                )
            }
        )
    }
}
