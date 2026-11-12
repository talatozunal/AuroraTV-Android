package com.aurora.iptv.ui.util

import android.view.KeyEvent

fun isGamepadActivate(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_BUTTON_START ||
        keyCode == KeyEvent.KEYCODE_BUTTON_SELECT

fun isGamepadBack(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BUTTON_B
