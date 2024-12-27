package ac.grim.grimac.checks.impl;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import lombok.SneakyThrows;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.*;

public class PacketLogger extends Check implements PacketCheck {
    public PacketLogger(GrimPlayer player) {
        super(player);
    }

    private static final Map<PacketTypeCommon, Class<? extends PacketWrapper<?>>> map = new HashMap<>();

    @SneakyThrows
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Class<? extends PacketWrapper<?>> clazz = map.get(packetType);
        StringBuilder message = new StringBuilder(packetType + "");
        if (clazz != null) {
            PacketWrapper<?> packet = clazz.getDeclaredConstructor(PacketReceiveEvent.class).newInstance(event);
            message.append("{");
            var fields = List.of(clazz.getDeclaredFields()).iterator();
            while (fields.hasNext()) {
                Field field = fields.next();
                field.setAccessible(true);
                message.append(field.getName()).append("=").append(field.get(packet));
                if (fields.hasNext()) message.append(",");
            }
            message.append("}");
        }

        log(message.toString());
    }

    @SneakyThrows
    private void log(Object o) {
        var path = GrimAPI.INSTANCE.getPlugin().getDataFolder().getPath() + "\\packetlog";
        new File(path).mkdir();
        File file = new File(path + "\\" + player.getName() + ".txt");
        file.createNewFile();
        Writer output = new BufferedWriter(new FileWriter(file, true));
        output.append(o + "\n");
        output.close();
    }

    static {
        map.put(INTERACT_ENTITY, WrapperPlayClientInteractEntity.class);
        map.put(PLAYER_DIGGING, WrapperPlayClientPlayerDigging.class);
        map.put(USE_ITEM, WrapperPlayClientUseItem.class);
        map.put(PLAYER_BLOCK_PLACEMENT, WrapperPlayClientPlayerBlockPlacement.class);
    }
}
