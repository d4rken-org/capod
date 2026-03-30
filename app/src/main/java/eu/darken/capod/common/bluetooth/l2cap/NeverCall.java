package eu.darken.capod.common.bluetooth.l2cap;

import androidx.annotation.Keep;

/**
 * Helper class for ART method size calculation.
 * Methods a() and b() must be adjacent in ART's internal method array.
 * Plain Java (not Kotlin) to avoid synthetic bridge methods from companion objects.
 */
@Keep
class NeverCall {
    private static void a() { throw new RuntimeException(); }
    private static void b() { throw new RuntimeException(); }
}
