package me.nagasonic.alkatraz.playerdata.profiles.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile.WarpPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarpPointSerializationTest {

    private static final WarpPoint POINT =
            new WarpPoint(2, "world", 120.5, 64.0, -45.3, 90.0f, 0.0f);

    @Test
    void serializeFormat() {
        assertEquals("2:world:120.5:64.0:-45.3:90.0:0.0", WarpPoint.serialize(POINT));
    }

    @Test
    void parseRoundTrip() {
        WarpPoint parsed = WarpPoint.parse(WarpPoint.serialize(POINT));
        assertEquals(POINT, parsed);
    }

    @Test
    void parseExactFields() {
        WarpPoint parsed = WarpPoint.parse("0:nether:-10.5:70.0:300.25:180.0:45.0");
        assertEquals(0, parsed.slot());
        assertEquals("nether", parsed.world());
        assertEquals(-10.5, parsed.x());
        assertEquals(70.0, parsed.y());
        assertEquals(300.25, parsed.z());
        assertEquals(180.0f, parsed.yaw());
        assertEquals(45.0f, parsed.pitch());
    }

    @Test
    void parseRejectsMissingYawPitch() {
        assertThrows(IndexOutOfBoundsException.class, () -> WarpPoint.parse("1:world:1.0:2.0:3.0"));
    }

    @Test
    void parseMalformedThrows() {
        assertThrows(NumberFormatException.class, () -> WarpPoint.parse("not-a-valid-entry"));
        assertThrows(NumberFormatException.class, () -> WarpPoint.parse("1:world:abc:0:0:0:0"));
    }
}