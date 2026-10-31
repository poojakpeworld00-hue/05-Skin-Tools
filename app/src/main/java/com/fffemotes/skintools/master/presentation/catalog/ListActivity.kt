package com.fffemotes.skintools.master.presentation.catalog

import com.fffemotes.skintools.ads.Ads
import com.fffemotes.skintools.ads.analytics.Screens
import com.fffemotes.skintools.ads.analytics.Analytics
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.fffemotes.skintools.master.presentation.common.enableDarkEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.fffemotes.skintools.master.databinding.ActivityListBinding
import com.fffemotes.skintools.master.domain.model.CatalogType
import com.fffemotes.skintools.master.presentation.common.appViewModel
import com.fffemotes.skintools.master.presentation.common.padForSystemBars
import com.fffemotes.skintools.master.presentation.common.listAd

/** Category grid (weapon types, maps, emote rarities) or item grid for one catalog section. */
class ListActivity : AppCompatActivity() {
    private val viewModel by appViewModel { handle -> ListViewModel(catalogRepository, handle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        val b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.padForSystemBars()
        b.header.back.setOnClickListener { finish() }

        val vm = viewModel
        b.header.title.text = vm.title.resolve(this)
        b.list.layoutManager = GridLayoutManager(this, vm.columns)
        // Per-list RC place for the in-list ad: screens.list_<type>, and screens.list_<type>_items inside a category.
        val listPlace = "screens.list_${vm.type.name.lowercase()}" + if (!vm.showsGroups && vm.hasGroups) "_items" else ""
        b.list.adapter = TileAdapter(vm.tiles, vm.shape, listAd(listPlace)) { i ->
            Analytics.click("item", i)
            Ads.exit(this, PLACE) {
                startActivity(
                    if (vm.showsGroups) intent(this, vm.type, i)
                    else DetailActivity.intent(this, vm.type, vm.groupIndex, i)
                )
            }
        }
        Ads.bottom(this, b.adSlot, PLACE)
    }

    override fun onResume() {
        super.onResume()
        Screens.show("list_${viewModel.type.name.lowercase()}")
    }

    companion object {
        private const val PLACE = "screens.list"
        const val EXTRA_CATALOG = "catalog"
        const val EXTRA_GROUP = "group"

        fun intent(context: Context, catalog: CatalogType, group: Int = -1): Intent =
            Intent(context, ListActivity::class.java)
                .putExtra(EXTRA_CATALOG, catalog.ordinal)
                .putExtra(EXTRA_GROUP, group)
    }
}
