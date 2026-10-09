package dev.redstoneengineering.integration.ldlib;

import com.lowdragmc.lowdraglib2.plugin.ILDLibPlugin;
import com.lowdragmc.lowdraglib2.plugin.LDLibPlugin;
import dev.redstoneengineering.RedstoneEngineering;

/**
 * RSE entry point for LDLib2-backed engineering UI infrastructure.
 *
 * <p>Physics, topology, sampling, control, and runtime evidence remain
 * server-authoritative RSE systems. LDLib2 owns presentation/layout/binding
 * infrastructure only.</p>
 */
@LDLibPlugin
public final class RseLdLibPlugin implements ILDLibPlugin {
    @Override
    public void onLoad() {
        RedstoneEngineering.LOGGER.info("LDLib2 engineering UI platform loaded for RSE");
    }
}
