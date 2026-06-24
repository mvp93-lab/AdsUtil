package com.mvp93.adsutil.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.mvp93.adsutil.AdManager
import com.mvp93.adsutil.utils.AdsConstants

@Composable
fun ShimmerLoading(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp
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

    val shimmerColors = listOf(
        Color(0xFF1A1C1E),
        Color(0xFF2C2F33),
        Color(0xFF1A1C1E),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(x = translateAnim, y = translateAnim)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    )
}

@Composable
fun SmallNativeAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsConstants.AD_NATIVE_TEST_ID,
    screenTag: String = "default"
) {
    val context = LocalContext.current
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()
    
    if (!isAdsEnabled) return

    var nativeAd by remember { mutableStateOf(AdManager.getCachedNativeAd(screenTag)) }
    var isLoading by remember { mutableStateOf(nativeAd == null) }

    LaunchedEffect(adUnitId, screenTag) {
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

    if (isLoading) {
        ShimmerLoading(modifier = modifier, height = 80.dp)
    } else if (nativeAd != null) {
        val ad = nativeAd!!
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            factory = { context ->
                NativeAdView(context).apply {
                    val density = context.resources.displayMetrics.density
                    
                    // AdChoices View (Mandatory)
                    val adChoices = com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = android.view.Gravity.TOP or android.view.Gravity.END
                        }
                    }
                    this.adChoicesView = adChoices
                    this.addView(adChoices)

                    val root = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        val p = (16 * density).toInt()
                        setPadding(p, p, p, p)
                        setBackgroundColor(0xFF1A1C1E.toInt())
                        gravity = android.view.Gravity.CENTER_VERTICAL
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

                    // Ad Badge for Small layout
                    val adBadge = TextView(context).apply {
                        text = "Ad"
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 10f
                        setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
                        setBackground(android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF1872E8.toInt())
                            cornerRadius = 4 * density
                        })
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = (2 * density).toInt()
                        }
                    }
                    textContainer.addView(adBadge)

                    val headline = TextView(context).apply {
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    this.headlineView = headline
                    textContainer.addView(headline)

                    val body = TextView(context).apply {
                        setTextColor(android.graphics.Color.GRAY)
                        textSize = 12f
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    this.bodyView = body
                    textContainer.addView(body)
                    root.addView(textContainer)

                    val cta = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            (36 * density).toInt()
                        ).apply {
                            leftMargin = (8 * density).toInt()
                        }
                        setTextColor(0xFFFFFFFF.toInt())
                        val shape = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF1872E8.toInt())
                            cornerRadius = 8 * density
                        }
                        background = shape
                        setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = android.view.Gravity.CENTER
                        isAllCaps = true
                    }
                    this.callToActionView = cta
                    root.addView(cta)

                    this.addView(root)
                }
            },
            update = { view ->
                val adView = view as NativeAdView
                (adView.headlineView as? TextView)?.text = ad.headline ?: "Sponsored"
                (adView.bodyView as? TextView)?.text = ad.body
                (adView.callToActionView as? TextView)?.text = ad.callToAction ?: "OPEN"
                
                ad.icon?.let {
                    (adView.iconView as? ImageView)?.load(it.uri) {
                        transformations(RoundedCornersTransformation(24f))
                    }
                }

                adView.registerNativeAd(ad, null)
            }
        )
    }
}

@Composable
fun BigNativeAd(
    modifier: Modifier = Modifier,
    adUnitId: String = AdsConstants.AD_NATIVE_TEST_ID,
    screenTag: String = "default"
) {
    val context = LocalContext.current
    val isAdsEnabled by AdManager.isAdsEnabled.collectAsState()

    if (!isAdsEnabled) return

    var nativeAd by remember { mutableStateOf(AdManager.getCachedNativeAd(screenTag)) }
    var isLoading by remember { mutableStateOf(nativeAd == null) }

    LaunchedEffect(adUnitId, screenTag) {
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

    if (isLoading) {
        ShimmerLoading(modifier = modifier, height = 250.dp)
    } else if (nativeAd != null) {
        val ad = nativeAd!!
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            factory = { context ->
                NativeAdView(context).apply {
                    val density = context.resources.displayMetrics.density

                    // AdChoices View (Mandatory)
                    val adChoices = com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = android.view.Gravity.TOP or android.view.Gravity.END
                        }
                    }
                    this.adChoicesView = adChoices
                    this.addView(adChoices)

                    val root = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setBackgroundColor(0xFF1A1C1E.toInt())
                    }

                    // Media Container
                    val mediaContainer = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (180 * density).toInt()
                        )
                    }

                    val media = MediaView(context).apply {
                        tag = "media_view"
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    mediaContainer.addView(media)

                    // Ad Badge for Big layout
                    val adBadge = TextView(context).apply {
                        text = "Ad"
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 10f
                        setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
                        setBackground(android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF1872E8.toInt())
                            cornerRadius = 4 * density
                        })
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
                        gravity = android.view.Gravity.CENTER_VERTICAL
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
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        maxLines = 1
                    }
                    this.headlineView = headline
                    textContainer.addView(headline)

                    val body = TextView(context).apply {
                        setTextColor(android.graphics.Color.GRAY)
                        textSize = 13f
                        maxLines = 2
                    }
                    this.bodyView = body
                    textContainer.addView(body)
                    bottomRow.addView(textContainer)
                    root.addView(bottomRow)

                    val cta = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (48 * density).toInt()
                        ).apply {
                            val margin = (12 * density).toInt()
                            setMargins(margin, 0, margin, margin)
                        }
                        setTextColor(0xFFFFFFFF.toInt())
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xFF1872E8.toInt())
                            cornerRadius = 12 * density
                        }
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = android.view.Gravity.CENTER
                        isAllCaps = true
                    }
                    this.callToActionView = cta
                    root.addView(cta)

                    this.addView(root)
                }
            },
            update = { view ->
                val adView = view as NativeAdView
                (adView.headlineView as? TextView)?.text = ad.headline ?: "Sponsored"
                (adView.bodyView as? TextView)?.text = ad.body
                (adView.callToActionView as? TextView)?.text = ad.callToAction ?: "LEARN MORE"
                
                ad.icon?.let {
                    (adView.iconView as? ImageView)?.load(it.uri) {
                        transformations(RoundedCornersTransformation(50f))
                    }
                }

                val mediaView = adView.findViewWithTag<MediaView>("media_view")
                adView.registerNativeAd(ad, mediaView)
            }
        )
    }
}
