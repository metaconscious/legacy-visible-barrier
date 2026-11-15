package io.github.metaconscious.lvb.core;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class RenderingPreferencesTest {
    @Test
    public void defaultsAliasesAndOverridesPreserveStartupBehavior() {
        Map<String, String> values = new HashMap<>();
        assertEquals(RenderingPreferences.DEFAULTS, RenderingPreferences.resolve(values, null, null));
        values.put("renderMode", "enhanced");
        values.put("nativeRefresh", "reload");
        assertEquals("enhanced", RenderingPreferences.resolve(values, null, null).renderMode);
        assertEquals(RenderingPolicy.Mode.ENHANCED, RenderingPreferences.resolve(values, null, null).initialMode());
        values.put("renderMode", "invalid");
        assertEquals(RenderingPreferences.SAFE, RenderingPreferences.resolve(values, "native", "dirty"));
        try { RenderingPreferences.resolve(values, null, null); fail("Invalid mode accepted"); }
        catch (IllegalArgumentException expected) { /* Only effective values are validated. */ }
        try { RenderingPreferences.resolve(values, "off", "invalid"); fail("Invalid refresh accepted"); }
        catch (IllegalArgumentException expected) { /* Invalid overrides retain the safe startup path. */ }
    }

    @Test
    public void draftResetAndUnrelatedEditsPreserveSavedValuesAndAliases() {
        RenderingPreferences saved = new RenderingPreferences("enhanced", "dirty");
        SettingsDraft draft = new SettingsDraft(saved);
        draft.cycleRefresh();
        assertEquals(new RenderingPreferences("enhanced", "reload"), draft.value());
        assertSame(saved, draft.saved());
        assertTrue(draft.dirty());
        draft.cycleRefresh();
        assertFalse(draft.dirty());
        draft.cycleMode();
        assertEquals("native", draft.value().renderMode);
        draft.cycleMode();
        assertEquals("off", draft.value().renderMode);
        draft.reset();
        assertEquals(RenderingPreferences.DEFAULTS, draft.value());
        assertEquals(saved, new SettingsDraft(saved).value());
        assertTrue(saved.sameBehavior(RenderingPreferences.DEFAULTS));
    }
}
