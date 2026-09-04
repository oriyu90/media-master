package com.example.ui

import androidx.annotation.StringRes
import com.example.R

/** Photo/video colour adjustments. Labels are localised (were hard-coded Japanese). */
enum class AdjustmentType(@StringRes val labelRes: Int) {
    EXPOSURE(R.string.adj_exposure),
    BRIGHTNESS(R.string.adj_brightness),
    CONTRAST(R.string.adj_contrast),
    BRILLIANCE(R.string.adj_brilliance),
    SHARPNESS(R.string.adj_sharpness),
    BLACK_POINT(R.string.adj_black_point),
    WHITE_POINT(R.string.adj_white_point),
    SATURATION(R.string.adj_saturation),
    HUE(R.string.adj_hue),
}
