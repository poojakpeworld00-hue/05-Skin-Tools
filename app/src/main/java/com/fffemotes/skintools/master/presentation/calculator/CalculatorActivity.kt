package com.fffemotes.skintools.master.presentation.calculator

import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.analytics.Analytics
import android.content.Intent
import android.os.Bundle
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.fffemotes.skintools.master.R
import com.fffemotes.skintools.master.databinding.ActivityCalcBinding
import com.fffemotes.skintools.master.databinding.ActivityListBinding
import com.fffemotes.skintools.master.domain.model.CalculatorType
import com.fffemotes.skintools.master.presentation.catalog.Tile
import com.fffemotes.skintools.master.presentation.catalog.TileAdapter
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.collectWhenStarted
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.listAd

/** The four calculators as tiles. Static, so it has no ViewModel. */
class CalculatorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }
        b.header.title.setText(R.string.tool_calculator)
        b.list.layoutManager = GridLayoutManager(this, 2)
        b.list.adapter = TileAdapter(CalculatorType.entries.map { Tile(getString(it.title), it.image) }, ad = listAd(PLACE)) {
            Analytics.click("calculator", CalculatorType.entries[it].name.lowercase())
            Ads.exit(this, PLACE) { startActivity(Intent(this, CalcActivity::class.java).putExtra(CalcActivity.EXTRA_CALC, it)) }
        }
        Ads.bottom(this, b.adSlot, PLACE)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("calculator")
    }

    private companion object {
        const val PLACE = "screens.calculator"
    }
}

class CalcActivity : AppCompatActivity() {
    private val viewModel by appViewModel { handle -> CalcViewModel(calculate, handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityCalcBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }

        b.header.title.setText(viewModel.type.title)
        b.inputLayout.setHint(viewModel.type.inputHint)
        listOf(b.chip50 to 50, b.chip100 to 100, b.chip500 to 500, b.chip1000 to 1000).forEach { (chip, v) ->
            chip.setOnClickListener { b.amount.setText(v.toString()); b.amount.setSelection(b.amount.length()) }
        }
        b.calculate.setOnClickListener { viewModel.onCalculate(b.amount.text?.toString().orEmpty()) }
        collectWhenStarted(viewModel.state) { state ->
            b.result.text = state.result.format(this)
            b.inputLayout.error = state.error?.let(::getString)
        }
        Ads.list(this, b.adList, "screens.calc") // above the Calculate button
        Ads.bottom(this, b.adSlot, "screens.calc")
    }

    override fun onResume() {
        super.onResume()
        Screens.show("calc")
    }

    companion object {
        const val EXTRA_CALC = "calc"
    }
}
