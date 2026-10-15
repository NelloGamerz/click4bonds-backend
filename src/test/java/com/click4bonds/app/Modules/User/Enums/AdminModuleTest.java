package com.click4bonds.app.Modules.User.Enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * How a module name written in a {@code @PreAuthorize} literal resolves.
 *
 * <p>The constants appear as strings in permission expressions, where nothing
 * checks them at compile time, so the resolution has to be exact and has to fail
 * to null rather than throw — a typo should refuse one endpoint, not turn it
 * into a 500.</p>
 */
class AdminModuleTest {

    @Test
    void everyConstantResolvesFromItsOwnName() {

        for (AdminModule module : AdminModule.values()) {
            assertEquals(module, AdminModule.fromName(module.name()));
        }
    }

    @Test
    void aNameThatIsNotAConstantResolvesToNull() {

        assertNull(AdminModule.fromName("ANALYTIC"));
        assertNull(AdminModule.fromName("analytics"));
        assertNull(AdminModule.fromName(""));
        assertNull(AdminModule.fromName(null));
    }
}
