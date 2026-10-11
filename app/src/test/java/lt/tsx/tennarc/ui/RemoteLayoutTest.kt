package lt.tsx.tennarc.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteLayoutTest {
    @Test fun phonesUprightKeepTheirColumn() {
        assertEquals(RemoteLayout.Upright, remoteLayout(385.dp, 917.dp, null))
        assertEquals(RemoteLayout.Upright, remoteLayout(360.dp, 640.dp, null))
        assertEquals(RemoteLayout.Upright, remoteLayout(344.dp, 882.dp, null))
    }

    @Test fun phonesOnTheirSideGoSideways() {
        assertEquals(RemoteLayout.Sideways, remoteLayout(917.dp, 385.dp, null))
        assertEquals(RemoteLayout.Sideways, remoteLayout(891.dp, 411.dp, null))
    }

    @Test fun aSmallWindowOnItsSideStaysOneColumn() {
        assertEquals(RemoteLayout.Upright, remoteLayout(450.dp, 380.dp, null))
    }

    @Test fun unfoldedAndTabletsGetPanes() {
        assertEquals(RemoteLayout.Panes(336.5.dp, 336.5.dp), remoteLayout(673.dp, 841.dp, null))
        assertEquals(RemoteLayout.Panes(400.dp, 400.dp), remoteLayout(800.dp, 1280.dp, null))
        assertEquals(RemoteLayout.Panes(840.dp, 840.dp), remoteLayout(1280.dp, 800.dp, null))
    }

    @Test fun aBookSplitsAtItsSpine() {
        val spine = Fold(330.dp, 0.dp, 350.dp, 841.dp, vertical = true, separating = true)
        assertEquals(RemoteLayout.Panes(330.dp, 350.dp), remoteLayout(673.dp, 841.dp, spine))
        assertEquals(RemoteLayout.Panes(330.dp, 350.dp), remoteLayout(673.dp, 841.dp, spine.copy(separating = false)))
        assertEquals(RemoteLayout.Panes(323.dp, 343.dp), remoteLayout(673.dp, 841.dp, spine, rtl = true))
    }

    @Test fun aSpineFarOffToTheSideIsIgnored() {
        val spine = Fold(200.dp, 0.dp, 200.dp, 841.dp, vertical = true, separating = true)
        assertEquals(RemoteLayout.Panes(336.5.dp, 336.5.dp), remoteLayout(673.dp, 841.dp, spine))
    }

    @Test fun halfOpenOnATableIsTabletop() {
        val hinge = Fold(0.dp, 336.dp, 841.dp, 337.dp, vertical = false, separating = true)
        assertEquals(RemoteLayout.Tabletop(336.dp, 337.dp), remoteLayout(841.dp, 673.dp, hinge))
        val flip = Fold(0.dp, 457.dp, 412.dp, 457.dp, vertical = false, separating = true)
        assertEquals(RemoteLayout.Tabletop(457.dp, 457.dp), remoteLayout(412.dp, 915.dp, flip))
    }

    @Test fun aHingeAcrossLyingFlatIsJustAScreen() {
        val hinge = Fold(0.dp, 336.dp, 841.dp, 336.dp, vertical = false, separating = false)
        assertEquals(RemoteLayout.Panes(420.5.dp, 420.5.dp), remoteLayout(841.dp, 673.dp, hinge))
        assertEquals(RemoteLayout.Upright, remoteLayout(412.dp, 915.dp, hinge.copy(right = 412.dp, top = 457.dp, bottom = 457.dp)))
    }

    @Test fun halvesTooShortForTabletopAreOneScreen() {
        val low = Fold(0.dp, 600.dp, 412.dp, 600.dp, vertical = false, separating = true)
        assertEquals(RemoteLayout.Upright, remoteLayout(412.dp, 760.dp, low))
    }

    @Test fun onlyTheDeckLayoutsRollTheirDeck() {
        assertEquals(true, RemoteLayout.Upright.rollsDeck)
        assertEquals(true, RemoteLayout.Sideways.rollsDeck)
        assertEquals(false, RemoteLayout.Panes(400.dp, 400.dp).rollsDeck)
        assertEquals(false, RemoteLayout.Tabletop(400.dp, 400.dp).rollsDeck)
    }

    @Test fun sheetsRiseFromTheirPane() {
        val window = DpSize(1280.dp, 800.dp)
        val panes = RemoteLayout.Panes(840.dp, 840.dp)
        assertEquals(SheetSpot(560.dp, (-220).dp), sheetSpot(Sheet.SETTINGS, panes, window))
        assertEquals(SheetSpot(440.dp, 420.dp), sheetSpot(Sheet.MORE, panes, window))
        val book = RemoteLayout.Panes(330.dp, 350.dp)
        assertEquals(SheetSpot(330.dp, (-171.5).dp), sheetSpot(Sheet.POWER, book, DpSize(673.dp, 841.dp)))
    }

    @Test fun onATableSheetsStayOnTheFlatHalf() {
        val tabletop = RemoteLayout.Tabletop(336.dp, 337.dp)
        val window = DpSize(841.dp, 673.dp)
        assertEquals(SheetSpot(560.dp, 0.dp, 336.dp), sheetSpot(Sheet.SETTINGS, tabletop, window))
        assertEquals(SheetSpot(420.5.dp, (-210.25).dp, 336.dp), sheetSpot(Sheet.MORE, tabletop, window))
        val flip = RemoteLayout.Tabletop(457.dp, 457.dp)
        assertEquals(SheetSpot(412.dp, 0.dp, 458.dp), sheetSpot(Sheet.MORE, flip, DpSize(412.dp, 915.dp)))
    }

    @Test fun aPhoneOnItsSideKeepsSheetsOffThePad() {
        assertEquals(SheetSpot(458.5.dp, (-229.25).dp), sheetSpot(Sheet.MORE, RemoteLayout.Sideways, DpSize(917.dp, 385.dp)))
    }

    @Test fun phonesUprightKeepMaterialsSheet() {
        assertNull(sheetSpot(Sheet.SETTINGS, RemoteLayout.Upright, DpSize(385.dp, 917.dp)))
    }
}
