package com.quarenta.calendario.ui.components

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HapticRulesTest {
    @Test
    fun newPhonesFeelTheDifferenceBetweenDoneAndDeleted() {
        assertEquals(HapticFeedbackConstants.CONFIRM, HapticRules.constant(Touch.Confirm, 34))
        assertEquals(HapticFeedbackConstants.REJECT, HapticRules.constant(Touch.Reject, 34))
        assertNotEquals(HapticRules.constant(Touch.Confirm, 34), HapticRules.constant(Touch.Reject, 34))
    }

    @Test
    fun olderPhonesFallBackToVibrationsTheyHave() {
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, HapticRules.constant(Touch.Confirm, 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticRules.constant(Touch.Reject, 26))
        assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, HapticRules.constant(Touch.Tick, 26))
    }

    @Test
    fun longPressIsAlwaysTheLongPressVibration() {
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticRules.constant(Touch.Long, 26))
        assertEquals(HapticFeedbackConstants.LONG_PRESS, HapticRules.constant(Touch.Long, 35))
    }
}
