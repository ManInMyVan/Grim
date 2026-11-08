package ac.grim.grimac.checks.impl.multiactions;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.RotationListener;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.RotationUpdate;
import org.jetbrains.annotations.NotNull;

// cinematic camera doesn't apply in inventories.
@CheckData(name = "MultiActionsO", stableKey = "grim.multiactions.inventory_rotation", description = "Rotating while in an inventory", experimental = true)
public class MultiActionsO extends Check implements RotationListener {

    public MultiActionsO(GrimPlayer player) {
        super(player);
    }

    @Override
    public void process(@NotNull RotationUpdate rotationUpdate) {
        if (player.packetStateData.lastPacketWasTeleport) return;
        // After a teleport the client resends the rotation it was given, since its own last sent rotation is from before
        // the teleport. That and any other look packet that doesn't turn isn't rotating
        if (rotationUpdate.deltaYawABS() == 0 && rotationUpdate.deltaPitchABS() == 0) return;
        if (!player.openWindow.mustBeOpen() || player.openWindow.getTicksOpen() == 0) return;
        if (flag() && shouldModifyPackets()) {
            player.closeInventory();
        }
    }
}
