package ru.r3xed.qsolog.keys

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyMapTest {
    @AfterTest
    fun back() {
        KeyMap.custom = emptyMap()
    }

    @Test
    fun defaultsHaveNoConflicts() {
        KeyAction.entries.forEach { a ->
            a.defaults.forEach { c -> assertEquals(emptyList(), KeyMap.conflicts(a, c, emptyMap()), "$a ${c.encode()}") }
        }
    }

    @Test
    fun labels() {
        assertEquals("Ctrl+N · F9", KeyMap.label(KeyAction.NEW_QSO, mac = false))
        assertEquals("⇧⌘E", KeyMap.label(KeyAction.EXPORT_ADIF, mac = true))
        assertEquals("Alt+W · Ctrl+Esc", KeyMap.label(KeyAction.CLEAR_CARD, mac = false))
    }

    @Test
    fun assignTakesTheKeyFromAnotherAction() {
        val m = KeyMap.assign(emptyMap(), KeyAction.SAVE, listOf(Chord("F2")))
        assertEquals(listOf(Chord("F2")), m[KeyAction.SAVE])
        assertEquals(emptyList(), m[KeyAction.FOCUS_CALL])
        // The CW messages are on top of the card's keys, not in conflict with them.
        assertEquals(listOf(Chord("F2")), m[KeyAction.CW_2] ?: KeyAction.CW_2.defaults)
        // Back to the default: not stored.
        assertNull(KeyMap.assign(m, KeyAction.SAVE, listOf(Chord("F12")))[KeyAction.SAVE])
    }

    @Test
    fun encodeDecode() {
        val m = mapOf(
            KeyAction.SETTINGS to listOf(Chord(",", ctrl = true), Chord(";", alt = true)),
            KeyAction.CLEAR_CARD to emptyList(),
            KeyAction.SEARCH to listOf(Chord("+", ctrl = true, shift = true)),
        )
        assertEquals(m, KeyMap.decode(KeyMap.encode(m)))
        assertEquals(emptyMap(), KeyMap.decode("junk|NO_SUCH=F1"))
    }

    @Test
    fun hitUsesTheUsersKeys() {
        KeyMap.custom = mapOf(KeyAction.NEW_QSO to listOf(Chord("Insert".take(1), ctrl = true)))
        assertTrue(KeyMap.hit(KeyAction.NEW_QSO, Chord("I", ctrl = true)))
        assertTrue(!KeyMap.hit(KeyAction.NEW_QSO, Chord("F9")))
    }

    @Test
    fun lettersAreForTyping() {
        assertNotNull(KeyMap.refusal(KeyAction.SAVE, Chord("S")))
        assertNull(KeyMap.refusal(KeyAction.SAVE, Chord("S", ctrl = true)))
        assertNull(KeyMap.refusal(KeyAction.H_FAVORITE, Chord("S")))
    }
}
