package com.wardrobe.atelier.ui

import com.wardrobe.atelier.presentation.EnglishTemplates
import org.junit.Assert.*
import org.junit.Test

class EnglishTemplatesTest {
    @Test fun countsAndPackingProgressKeepTheirValues() {
        assertEquals("12 selected", EnglishTemplates.translate("已选 12 件"))
        assertEquals("5 items · 3 outfits · 2/5 packed", EnglishTemplates.translate("5 件 · 3 套 · 已装 2/5"))
        assertEquals("3 outfits using 5 pieces.", EnglishTemplates.translate("用 5 件衣物组成 3 套穿搭。"))
    }
    @Test fun unmatchedContentIsNotTranslated() {
        assertNull(EnglishTemplates.translate("妈妈送的外套"))
        assertNull(EnglishTemplates.translate("My shirt"))
        assertNull(EnglishTemplates.translate("已选 12 件是衣物名字"))
    }
}
