package ac.grim.grimac.checks.impl;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.data.Pair;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import lombok.SneakyThrows;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.*;

public class PacketLogger extends Check implements PacketCheck {
    public PacketLogger(GrimPlayer player) {
        super(player);
    }

    private static final Map<PacketTypeCommon, Function<PacketReceiveEvent, Pair<String, Map<String, ?>>>> values = new HashMap<>();

    private long last = -1;

    private static <P extends PacketWrapper<?>> Function<PacketReceiveEvent, Pair<String, Map<String, ?>>> fields(Function<PacketReceiveEvent, P> get, BiPredicate<String, P> ignored) {
        return new Function<>() {
            private Field[] fields;

            @SneakyThrows
            @Override
            public Pair<String, Map<String, ?>> apply(PacketReceiveEvent event) {
                P packet = get.apply(event);
                HashMap<String, Object> map = new HashMap<>();

                if (fields == null) {
                    fields = packet.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        field.setAccessible(true);
                    }
                }

                for (Field field : fields) {
                    if (!ignored.test(field.getName(), packet)) {
                        map.put(field.getName(), field.get(packet));
                    }
                }

                return new Pair<>(event.getPacketType().toString(), map);
            }
        };
    }

    @SneakyThrows
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        PacketTypeCommon packetType = event.getPacketType();
        Function<PacketReceiveEvent, Pair<String, Map<String, ?>>> get = values.get(packetType);
        StringBuilder message = new StringBuilder();
        if (get != null) {
            Pair<String, Map<String, ?>> pair = get.apply(event);
            Iterator<? extends Map.Entry<String, ?>> values = pair.second().entrySet().iterator();
            message.append(pair.first()).append("{");
            while (values.hasNext()) {
                Map.Entry<String, ?> value = values.next();
                message.append(value.getKey()).append("=").append(value.getValue());
                if (values.hasNext()) message.append(",");
            }
            message.append("}");
        } else {
            message.append(packetType);
        }

        log(message.toString());
    }

    @SneakyThrows
    private void log(Object o) {
        String path = GrimAPI.INSTANCE.getPlugin().getDataFolder().getPath() + "\\packetlog";
        new File(path).mkdir();
        File file = new File(path + "\\" + player.getName() + ".txt");
        file.createNewFile();
        Writer output = new BufferedWriter(new FileWriter(file, true));
        long now = System.currentTimeMillis();
        long delay = last == -1 ? 0 : now - last;
        last = now;
        output.append(delay + "ms " + o + "\n");
        output.close();
    }

    static {
        values.put(INTERACT_ENTITY, event -> {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            Map<String, Object> map = new HashMap<>();
            map.put("entityID", wrapper.getEntityId());
            wrapper.getTarget().ifPresent(target -> map.put("target", target));
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                map.put("hand", wrapper.getHand());
            }
            wrapper.isSneaking().ifPresent(sneaking -> map.put("sneaking", sneaking));
            return new Pair<>(wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK ? "ATTACK" : "INTERACT", map);
        });
        values.put(PLAYER_DIGGING, fields(WrapperPlayClientPlayerDigging::new, (name, wrapper) -> name.equals("sequence") && wrapper.getServerVersion().isOlderThan(ServerVersion.V_1_19) || name.equals("blockFaceId")));
        values.put(USE_ITEM, fields(WrapperPlayClientUseItem::new, (name, wrapper) -> switch (name) {
            case "sequence" -> wrapper.getServerVersion().isOlderThan(ServerVersion.V_1_19);
            case "yaw", "pitch" -> wrapper.getServerVersion().isOlderThan(ServerVersion.V_1_21);
            default -> false;
        }));
        values.put(PLAYER_BLOCK_PLACEMENT, fields(WrapperPlayClientPlayerBlockPlacement::new, (name, wrapper) -> name.equals("faceId")));
    }
}
