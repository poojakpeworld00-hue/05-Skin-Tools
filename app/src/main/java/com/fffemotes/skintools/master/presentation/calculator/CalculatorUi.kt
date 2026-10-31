package com.fffemotes.skintools.master.presentation.calculator

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.CalcResult
import com.fffemotes.skintools.master.domain.model.CalculatorType
import java.util.Locale

@get:StringRes
val CalculatorType.title: Int
    get() = when (this) {
        CalculatorType.DIAMONDS_TO_DOLLARS -> R.string.calc_dollars
        CalculatorType.ELITE_PASS -> R.string.calc_elite_pass
        CalculatorType.DOLLARS_TO_DIAMONDS -> R.string.calc_diamonds
        CalculatorType.DAILY -> R.string.calc_daily
    }

@get:StringRes
val CalculatorType.inputHint: Int
    get() = when (this) {
        CalculatorType.DIAMONDS_TO_DOLLARS -> R.string.calc_input_diamonds
        CalculatorType.ELITE_PASS -> R.string.calc_input_passes
        CalculatorType.DOLLARS_TO_DIAMONDS -> R.string.calc_input_dollars
        CalculatorType.DAILY -> R.string.calc_input_days
    }

@get:DrawableRes
val CalculatorType.image: Int
    get() = when (this) {
        CalculatorType.DIAMONDS_TO_DOLLARS -> R.drawable.cal1
        CalculatorType.ELITE_PASS -> R.drawable.cal5
        CalculatorType.DOLLARS_TO_DIAMONDS -> R.drawable.cal7
        CalculatorType.DAILY -> R.drawable.cal6
    }

fun CalcResult.format(context: Context): String = when (this) {
    is CalcResult.Dollars -> String.format(Locale.US, "$%.2f", value)
    is CalcResult.Diamonds -> context.getString(R.string.calc_diamonds_result, value)
}
