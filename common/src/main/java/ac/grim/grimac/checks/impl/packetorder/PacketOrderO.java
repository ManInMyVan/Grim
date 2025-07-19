package ac.grim.grimac.checks.impl.packetorder;

import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;

@CheckData(name = "PacketOrderO", experimental = true)
public class PacketOrderO extends Check implements PacketCheck {
    public PacketOrderO(final GrimPlayer player) {
        super(player);
    }

    private boolean flying;

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            flying = false;
        }

        if (isFlying(event.getPacketType()) && player.supportsEndTickPreVia() && !player.packetStateData.lastPacketWasTeleport) {
            flying = true;
            return;
        }

        if (flying && event.getPacketType() != PacketType.Play.Client.KEEP_ALIVE) {
            flagAndAlert("type=" + event.getPacketType());
        }
    }
}
