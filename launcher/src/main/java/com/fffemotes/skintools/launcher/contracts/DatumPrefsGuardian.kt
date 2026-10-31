package com.fffemotes.skintools.launcher.contracts

import com.fffemotes.skintools.launcher.entities.LobbyLookerMatrixDatum

interface DatumPrefsGuardian {
    fun onAnyClick()
    fun hide(gridItem: LobbyLookerMatrixDatum)
    fun rename(gridItem: LobbyLookerMatrixDatum)
    fun resize(gridItem: LobbyLookerMatrixDatum)
    fun appInfoUi(gridItem: LobbyLookerMatrixDatum)
    fun remove(gridItem: LobbyLookerMatrixDatum)
    fun uninstall(gridItem: LobbyLookerMatrixDatum)
    fun onDismiss()
}

abstract class DatumPrefsGuardianConnector : DatumPrefsGuardian {
    override fun onAnyClick() = Unit
    override fun hide(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun rename(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun resize(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun appInfoUi(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun remove(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun uninstall(gridItem: LobbyLookerMatrixDatum) = Unit
    override fun onDismiss() = Unit
}
