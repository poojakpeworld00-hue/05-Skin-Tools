package com.fffemotes.skintools.master.domain.usecase

import com.fffemotes.skintools.master.domain.model.CalcResult
import com.fffemotes.skintools.master.domain.model.CalculatorType

/** Validates the typed amount and runs a diamond calculator. */
class CalculateUseCase {
    sealed interface Outcome {
        data object Empty : Outcome
        data object Invalid : Outcome
        data class Success(val result: CalcResult) : Outcome
    }

    operator fun invoke(type: CalculatorType, input: String): Outcome {
        val text = input.trim()
        if (text.isEmpty()) return Outcome.Empty
        val amount = text.toDoubleOrNull()
        if (amount == null || amount.isNaN() || amount.isInfinite() || amount < 0) return Outcome.Invalid
        return Outcome.Success(compute(type, amount))
    }

    fun compute(type: CalculatorType, amount: Double): CalcResult =
        if (type.toDollars) CalcResult.Dollars(amount / type.factor)
        else CalcResult.Diamonds((amount * type.factor).toLong())
}
