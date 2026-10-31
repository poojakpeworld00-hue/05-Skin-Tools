package com.fffemotes.skintools.master.presentation.calculator

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.domain.model.CalcResult
import com.fffemotes.skintools.master.domain.model.CalculatorType
import com.fffemotes.skintools.master.domain.usecase.CalculateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CalcUiState(val result: CalcResult, @StringRes val error: Int? = null)

class CalcViewModel(private val calculate: CalculateUseCase, args: SavedStateHandle) : ViewModel() {
    val type: CalculatorType = CalculatorType.entries[args[CalcActivity.EXTRA_CALC] ?: 0]

    private val _state = MutableStateFlow(CalcUiState(calculate.compute(type, 0.0)))
    val state: StateFlow<CalcUiState> = _state

    fun onCalculate(input: String) {
        _state.value = when (val outcome = calculate(type, input)) {
            CalculateUseCase.Outcome.Empty -> _state.value.copy(error = R.string.calc_error_empty)
            CalculateUseCase.Outcome.Invalid -> _state.value.copy(error = R.string.calc_error_invalid)
            is CalculateUseCase.Outcome.Success -> CalcUiState(outcome.result)
        }
    }
}
