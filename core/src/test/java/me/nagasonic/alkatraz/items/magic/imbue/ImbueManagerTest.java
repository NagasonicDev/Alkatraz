package me.nagasonic.alkatraz.items.magic.imbue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImbueManagerTest {

    @Test
    void wrapNameWrapsCleanNameInSymbols() {
        assertEquals("§d§k𖢻 §r§dDiamond Sword §d§k𖢻", ImbueManager.wrapName("Diamond Sword"));
    }

    @Test
    void wrapNameIsIdempotentForAlreadyWrappedNames() {
        assertEquals("§d§k𖢻 §r§dMy Sword §d§k𖢻", ImbueManager.wrapName("§d§k𖢻 §r§dMy Sword §d§k𖢻"));
    }

    @Test
    void wrapNameStripsLegacyImbuedPrefix() {
        assertEquals("§d§k𖢻 §r§dDiamond Sword §d§k𖢻", ImbueManager.wrapName("§dImbued §rDiamond Sword"));
    }

    @Test
    void unwrapNameRemovesNewWrapper() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName("§d§k𖢻 §r§dDiamond Sword §d§k𖢻"));
    }

    @Test
    void unwrapNameRemovesLegacyImbuedPrefix() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName("§dImbued §rDiamond Sword"));
    }

    @Test
    void unwrapNameKeepsPlainNamesUntouched() {
        assertEquals("My Sword", ImbueManager.unwrapName("My Sword"));
    }

    @Test
    void unwrapNameKeepsNamesThatStartWithImbuedWord() {
        assertEquals("Imbued Blade", ImbueManager.unwrapName("§d§k𖢻 §r§dImbued Blade §d§k𖢻"));
    }

    @Test
    void unwrapNameHandlesNullOrEmpty() {
        assertEquals("", ImbueManager.unwrapName(null));
        assertEquals("", ImbueManager.unwrapName(""));
        assertEquals("", ImbueManager.unwrapName("𖢻"));
    }

    @Test
    void wrapNameHandlesNullOrEmpty() {
        assertEquals("", ImbueManager.wrapName(null));
        assertEquals("", ImbueManager.wrapName(""));
    }

    @Test
    void wrapAndUnwrapAreInverseForCleanNames() {
        assertEquals("Diamond Sword", ImbueManager.unwrapName(ImbueManager.wrapName("Diamond Sword")));
        assertEquals("My Sword", ImbueManager.unwrapName(ImbueManager.wrapName("My Sword")));
    }
}
