package ac.grim.grimac.checks.impl;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.checks.type.PacketCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import lombok.SneakyThrows;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class PacketLogger extends Check implements PacketCheck {
    private static final Map<PacketTypeCommon, PacketWriter<PacketReceiveEvent>> vals_c = new HashMap<>();
    private static final Map<PacketTypeCommon, PacketWriter<PacketSendEvent>> vals_s = new HashMap<>();
    private static final boolean LOG_SENT = false;
    private static final boolean LOG_RECEIVED = true;
    private static final File dir = new File(GrimAPI.INSTANCE.getPlugin().getDataFolder().getPath() + "\\packetlog");
    private long last = -1;
    private final File file = new File(GrimAPI.INSTANCE.getPlugin().getDataFolder().getPath() + "\\packetlog\\" + player.getName() + ".txt");

    public PacketLogger(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (LOG_RECEIVED) {
            log(event, vals_c.get(event.getPacketType()), new StringBuilder("(Receive) "));
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (LOG_SENT) {
            log(event, vals_s.get(event.getPacketType()), new StringBuilder("(Send) "));
        }
    }

    private <E extends ProtocolPacketEvent> void log(E event, PacketWriter<E> writer, StringBuilder message) {
        message.append(event.getPacketType());

        if (writer != null) {
            HashMap<String, Object> fields = new HashMap<>();
            writer.write(event, fields);
            message.append(fields);
        }

        log(message);
    }

    @SneakyThrows
    private void log(Object o) {
        dir.mkdir();
        file.createNewFile();
        Writer output = new BufferedWriter(new FileWriter(file, true));
        long now = System.currentTimeMillis();
        long delay = last == -1 ? 0 : now - last;
        last = now;
        output.append(delay + "ms " + o + "\n");
        output.close();
    }

    public interface PacketWriter<E extends ProtocolPacketEvent> {
        void write(E event, Map<String, Object> fields);
    }

    @Contract(value = "_ -> new", pure = true)
    private static <P extends PacketWrapper<?>, E extends ProtocolPacketEvent> @NotNull PacketWriter<E> fields(Function<E, P> get) {
        return fields(get, (a, b) -> false);
    }

    @Contract(value = "_, _ -> new", pure = true)
    private static <P extends PacketWrapper<?>, E extends ProtocolPacketEvent> @NotNull PacketWriter<E> fields(Function<E, P> get, BiPredicate<String, P> ignored) {
        return new PacketWriter<>() {
            private Field[] fields;

            @SneakyThrows
            @Override
            public void write(E event, Map<String, Object> map) {
                P packet = get.apply(event);

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
            }
        };
    }

    private static void initC() {
        PacketWriter<PacketReceiveEvent> writer;
        vals_c.put(PacketType.Play.Client.INTERACT_ENTITY, (event, fields) -> {
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            fields.put("action", wrapper.getAction());
            fields.put("entityID", wrapper.getEntityId());
            wrapper.getTarget().ifPresent(target -> fields.put("target", target));
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                fields.put("hand", wrapper.getHand());
            }
            wrapper.isSneaking().ifPresent(sneaking -> fields.put("sneaking", sneaking));
        });
        vals_c.put(PacketType.Play.Client.SET_DIFFICULTY, (event, fields) -> fields.put("difficulty", new WrapperPlayClientSetDifficulty(event).getDifficulty()));
        vals_c.put(PacketType.Play.Client.CHAT_MESSAGE, (event, fields) -> {
            WrapperPlayClientChatMessage wrapper = new WrapperPlayClientChatMessage(event);
            fields.put("message", wrapper.getMessage());
            wrapper.getMessageSignData().ifPresent(messageSignData -> fields.put("messageSignData", messageSignData));
            if (wrapper.getLastSeenMessages() != null) {
                assert wrapper.getLegacyLastSeenMessages() == null;
                fields.put("messageSignData", wrapper.getLastSeenMessages());
            } else if (wrapper.getLegacyLastSeenMessages() != null) {
                fields.put("messageSignData", wrapper.getLegacyLastSeenMessages());
            }
        });
        vals_c.put(PacketType.Play.Client.CLOSE_WINDOW, (event, fields) -> fields.put("windowId", new WrapperPlayClientCloseWindow(event).getWindowId()));
        vals_c.put(PacketType.Play.Client.KEEP_ALIVE, (event, fields) -> fields.put("id", new WrapperPlayClientKeepAlive(event).getId()));
        vals_c.put(PacketType.Play.Client.LOCK_DIFFICULTY, (event, fields) -> fields.put("locked", new WrapperPlayClientLockDifficulty(event).isLocked()));
        vals_c.put(PacketType.Play.Client.PLAYER_POSITION, writer = (event, fields) -> {
            WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
            fields.put("onGround", wrapper.isOnGround());

            if (event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
                fields.put("horizontalCollision", wrapper.isHorizontalCollision());
            }

            if (wrapper.hasPositionChanged()) {
                fields.put("x", wrapper.getLocation().getX());
                fields.put("y", wrapper.getLocation().getY());
                fields.put("z", wrapper.getLocation().getZ());
            }

            if (wrapper.hasRotationChanged()) {
                fields.put("yaw", wrapper.getLocation().getYaw());
                fields.put("pitch", wrapper.getLocation().getPitch());
            }
        });
        vals_c.put(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION, writer);
        vals_c.put(PacketType.Play.Client.PLAYER_ROTATION, writer);
        vals_c.put(PacketType.Play.Client.PLAYER_FLYING, writer);
        vals_c.put(PacketType.Play.Client.CLIENT_TICK_END, null);
        vals_c.put(PacketType.Play.Client.CONFIGURATION_ACK, null);
        vals_c.put(PacketType.Play.Client.PLAYER_LOADED, null);
        vals_c.put(PacketType.Play.Client.HELD_ITEM_CHANGE, (event, fields) -> fields.put("slot", new WrapperPlayClientHeldItemChange(event).getSlot()));
        vals_c.put(PacketType.Play.Client.USE_ITEM, (event, fields) -> {
            WrapperPlayClientUseItem wrapper = new WrapperPlayClientUseItem(event);
            fields.put("hand", wrapper.getHand());
            if (event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_19)) {
                fields.put("sequence", wrapper.getSequence());
                if (event.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_21)) {
                    fields.put("yaw", wrapper.getYaw());
                    fields.put("pitch", wrapper.getPitch());
                }
            }
        });
        vals_c.put(PacketType.Play.Client.CLIENT_STATUS, (event, fields) -> fields.put("action", new WrapperPlayClientClientStatus(event).getAction()));
        vals_c.put(PacketType.Play.Client.CLIENT_SETTINGS, (event, fields) -> {
            WrapperCommonClientSettings<WrapperPlayClientSettings> wrapper = new WrapperCommonClientSettings<>(event);
            fields.put("locale", wrapper.getLocale());
            fields.put("viewDistance", wrapper.getViewDistance());
            fields.put("skinMask", wrapper.getSkinMask());
            if (event.getServerVersion() == ServerVersion.V_1_7_10) {
                fields.put("ignoredDifficulty", wrapper.getIgnoredDifficulty());
            }
            if (event.getServerVersion() .isNewerThanOrEquals(ServerVersion.V_1_9)) {
                fields.put("mainHand", wrapper.getMainHand());
                if (event.getServerVersion() .isNewerThanOrEquals(ServerVersion.V_1_17)) {
                    fields.put("textFilteringEnabled", wrapper.isTextFilteringEnabled());
                    if (event.getServerVersion() .isNewerThanOrEquals(ServerVersion.V_1_18)) {
                        fields.put("allowServerListings", wrapper.isServerListingAllowed());
                        if (event.getServerVersion() .isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
                            fields.put("particleStatus", wrapper.getParticleStatus());
                        }
                    }
                }
            }
        });
        vals_c.put(PacketType.Play.Client.TAB_COMPLETE, (event, fields) -> {
            WrapperPlayClientTabComplete wrapper = new WrapperPlayClientTabComplete(event);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_13)) {
                fields.put("transactionId", wrapper.getTransactionId().orElseThrow());
                fields.put("text", wrapper.getText());
            } else {
                fields.put("text", wrapper.getText());
                if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
                    fields.put("assumeCommand", wrapper.isAssumeCommand());
                }
                fields.put("blockPosition", wrapper.getBlockPosition().orElse(null));
            }
        });
        vals_c.put(PacketType.Play.Client.WINDOW_CONFIRMATION, (event, fields) -> {
            WrapperPlayClientWindowConfirmation wrapper = new WrapperPlayClientWindowConfirmation(event);
            fields.put("windowId", wrapper.getWindowId());
            fields.put("actionId", wrapper.getActionId());
            fields.put("accepted", wrapper.isAccepted());
        });
        vals_c.put(PacketType.Play.Client.CLICK_WINDOW_BUTTON, (event, fields) -> {
            WrapperPlayClientClickWindowButton wrapper = new WrapperPlayClientClickWindowButton(event);
            fields.put("windowId", wrapper.getWindowId());
            fields.put("buttonId", wrapper.getButtonId());
        });
        vals_c.put(PacketType.Play.Client.CLICK_WINDOW, (event, fields) -> {
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            fields.put("windowId", wrapper.getWindowId());
            wrapper.getStateId().ifPresent(stateId -> fields.put("stateId", stateId));
            fields.put("slot", wrapper.getSlot());
            fields.put("button", wrapper.getButton());
            wrapper.getActionNumber().ifPresent(actionNumber -> fields.put("actionNumber", actionNumber));
            fields.put("clickType", wrapper.getWindowClickType());
            wrapper.getSlots().ifPresent(slots -> fields.put("slots", slots));
            fields.put("stack", wrapper.getCarriedItemStack());
        });

        vals_c.put(PacketType.Play.Client.PLAYER_DIGGING, fields(WrapperPlayClientPlayerDigging::new, (name, wrapper) -> name.equals("sequence") && wrapper.getServerVersion().isOlderThan(ServerVersion.V_1_19) || name.equals("blockFaceId")));
        vals_c.put(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT, fields(WrapperPlayClientPlayerBlockPlacement::new, (name, wrapper) -> name.equals("faceId")));
        vals_c.put(PacketType.Play.Client.CHAT_PREVIEW, fields(WrapperPlayClientChatPreview::new));
        vals_c.put(PacketType.Play.Client.TELEPORT_CONFIRM, fields(WrapperPlayClientTeleportConfirm::new));
        vals_c.put(PacketType.Play.Client.QUERY_BLOCK_NBT, fields(WrapperPlayClientQueryBlockNBT::new));
        vals_c.put(PacketType.Play.Client.VEHICLE_MOVE, fields(WrapperPlayClientVehicleMove::new));
        vals_c.put(PacketType.Play.Client.STEER_BOAT, fields(WrapperPlayClientSteerBoat::new));
        vals_c.put(PacketType.Play.Client.PICK_ITEM, fields(WrapperPlayClientPickItem::new));
        vals_c.put(PacketType.Play.Client.PLUGIN_MESSAGE, fields(WrapperPlayClientPluginMessage::new));
        vals_c.put(PacketType.Play.Client.EDIT_BOOK, fields(WrapperPlayClientEditBook::new));
        vals_c.put(PacketType.Play.Client.QUERY_ENTITY_NBT, fields(WrapperPlayClientQueryEntityNBT::new));
        vals_c.put(PacketType.Play.Client.GENERATE_STRUCTURE, fields(WrapperPlayClientGenerateStructure::new));
        vals_c.put(PacketType.Play.Client.CRAFT_RECIPE_REQUEST, fields(WrapperPlayClientCraftRecipeRequest::new));
        vals_c.put(PacketType.Play.Client.PLAYER_ABILITIES, fields(WrapperPlayClientPlayerAbilities::new));
        vals_c.put(PacketType.Play.Client.ENTITY_ACTION, fields(WrapperPlayClientEntityAction::new));
        vals_c.put(PacketType.Play.Client.STEER_VEHICLE, fields(WrapperPlayClientSteerVehicle::new));
        vals_c.put(PacketType.Play.Client.PONG, fields(WrapperPlayClientPong::new));
        vals_c.put(PacketType.Play.Client.SET_DISPLAYED_RECIPE, fields(WrapperPlayClientSetDisplayedRecipe::new));
        vals_c.put(PacketType.Play.Client.SET_RECIPE_BOOK_STATE, fields(WrapperPlayClientSetRecipeBookState::new));
        vals_c.put(PacketType.Play.Client.NAME_ITEM, fields(WrapperPlayClientNameItem::new));
        vals_c.put(PacketType.Play.Client.RESOURCE_PACK_STATUS, fields(WrapperPlayClientResourcePackStatus::new));
        vals_c.put(PacketType.Play.Client.ADVANCEMENT_TAB, fields(WrapperPlayClientAdvancementTab::new));
        vals_c.put(PacketType.Play.Client.SELECT_TRADE, fields(WrapperPlayClientSelectTrade::new));
        vals_c.put(PacketType.Play.Client.SET_BEACON_EFFECT, fields(WrapperPlayClientSetBeaconEffect::new));
        vals_c.put(PacketType.Play.Client.UPDATE_COMMAND_BLOCK, fields(WrapperPlayClientUpdateCommandBlock::new));
        vals_c.put(PacketType.Play.Client.UPDATE_COMMAND_BLOCK_MINECART, fields(WrapperPlayClientUpdateCommandBlockMinecart::new));
        vals_c.put(PacketType.Play.Client.CREATIVE_INVENTORY_ACTION, fields(WrapperPlayClientCreativeInventoryAction::new));
        vals_c.put(PacketType.Play.Client.UPDATE_JIGSAW_BLOCK, fields(WrapperPlayClientUpdateJigsawBlock::new));
        vals_c.put(PacketType.Play.Client.UPDATE_SIGN, fields(WrapperPlayClientUpdateSign::new));
        vals_c.put(PacketType.Play.Client.ANIMATION, fields(WrapperPlayClientAnimation::new));
        vals_c.put(PacketType.Play.Client.SPECTATE, fields(WrapperPlayClientSpectate::new));
        vals_c.put(PacketType.Play.Client.CHAT_COMMAND, fields(WrapperPlayClientChatCommand::new));
        vals_c.put(PacketType.Play.Client.CHAT_ACK, fields(WrapperPlayClientChatAck::new));
        vals_c.put(PacketType.Play.Client.CHAT_SESSION_UPDATE, fields(WrapperPlayClientChatSessionUpdate::new));
        vals_c.put(PacketType.Play.Client.CHUNK_BATCH_ACK, fields(WrapperPlayClientChunkBatchAck::new));
        vals_c.put(PacketType.Play.Client.DEBUG_PING, fields(WrapperPlayClientDebugPing::new));
        vals_c.put(PacketType.Play.Client.SLOT_STATE_CHANGE, fields(WrapperPlayClientSlotStateChange::new));
        vals_c.put(PacketType.Play.Client.CHAT_COMMAND_UNSIGNED, fields(WrapperPlayClientChatCommandUnsigned::new));
        vals_c.put(PacketType.Play.Client.COOKIE_RESPONSE, fields(WrapperPlayClientCookieResponse::new));
        vals_c.put(PacketType.Play.Client.DEBUG_SAMPLE_SUBSCRIPTION, fields(WrapperPlayClientDebugSampleSubscription::new));
        vals_c.put(PacketType.Play.Client.SELECT_BUNDLE_ITEM, fields(WrapperPlayClientSelectBundleItem::new));
        vals_c.put(PacketType.Play.Client.PLAYER_INPUT, fields(WrapperPlayClientPlayerInput::new));
        vals_c.put(PacketType.Play.Client.PICK_ITEM_FROM_BLOCK, fields(WrapperPlayClientPickItemFromBlock::new));
        vals_c.put(PacketType.Play.Client.PICK_ITEM_FROM_ENTITY, fields(WrapperPlayClientPickItemFromEntity::new));
    }

    private static void initS() {
        vals_s.put(PacketType.Play.Server.SET_COMPRESSION, fields(WrapperPlayServerSetCompression::new));
        vals_s.put(PacketType.Play.Server.UPDATE_ENTITY_NBT, fields(WrapperPlayServerUpdateEntityNBT::new));
        vals_s.put(PacketType.Play.Server.USE_BED, fields(WrapperPlayServerUseBed::new));
        vals_s.put(PacketType.Play.Server.SPAWN_WEATHER_ENTITY, fields(WrapperPlayServerSpawnWeatherEntity::new));
        vals_s.put(PacketType.Play.Server.TITLE, fields(WrapperPlayServerTitle::new));
        vals_s.put(PacketType.Play.Server.WORLD_BORDER, fields(WrapperPlayServerWorldBorder::new));
        vals_s.put(PacketType.Play.Server.COMBAT_EVENT, fields(WrapperPlayServerCombatEvent::new));
        vals_s.put(PacketType.Play.Server.ENTITY_MOVEMENT, fields(WrapperPlayServerEntityMovement::new));
        vals_s.put(PacketType.Play.Server.SPAWN_LIVING_ENTITY, fields(WrapperPlayServerSpawnLivingEntity::new));
        vals_s.put(PacketType.Play.Server.SPAWN_PAINTING, fields(WrapperPlayServerSpawnPainting::new));
        vals_s.put(PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING, fields(WrapperPlayServerAcknowledgePlayerDigging::new));
        vals_s.put(PacketType.Play.Server.CHAT_PREVIEW_PACKET, fields(WrapperPlayServerChatPreview::new));
        vals_s.put(PacketType.Play.Server.PLAYER_CHAT_HEADER, fields(WrapperPlayServerPlayerChatHeader::new));
        vals_s.put(PacketType.Play.Server.PLAYER_INFO, fields(WrapperPlayServerPlayerInfo::new));
        vals_s.put(PacketType.Play.Server.DISPLAY_CHAT_PREVIEW, fields(WrapperPlayServerSetDisplayChatPreview::new));
        vals_s.put(PacketType.Play.Server.UPDATE_ENABLED_FEATURES, fields(WrapperPlayServerUpdateEnabledFeatures::new));
        vals_s.put(PacketType.Play.Server.SPAWN_PLAYER, fields(WrapperPlayServerSpawnPlayer::new));
        vals_s.put(PacketType.Play.Server.WINDOW_CONFIRMATION, fields(WrapperPlayServerWindowConfirmation::new));
        vals_s.put(PacketType.Play.Server.SPAWN_ENTITY, fields(WrapperPlayServerSpawnEntity::new));
        vals_s.put(PacketType.Play.Server.SPAWN_EXPERIENCE_ORB, fields(WrapperPlayServerSpawnExperienceOrb::new));
        vals_s.put(PacketType.Play.Server.ENTITY_ANIMATION, fields(WrapperPlayServerEntityAnimation::new));
        vals_s.put(PacketType.Play.Server.BLOCK_BREAK_ANIMATION, fields(WrapperPlayServerBlockBreakAnimation::new));
        vals_s.put(PacketType.Play.Server.BLOCK_ENTITY_DATA, fields(WrapperPlayServerBlockEntityData::new));
        vals_s.put(PacketType.Play.Server.BLOCK_ACTION, fields(WrapperPlayServerBlockAction::new));
        vals_s.put(PacketType.Play.Server.BLOCK_CHANGE, fields(WrapperPlayServerBlockChange::new));
        vals_s.put(PacketType.Play.Server.BOSS_BAR, fields(WrapperPlayServerBossBar::new));
        vals_s.put(PacketType.Play.Server.SERVER_DIFFICULTY, fields(WrapperPlayServerDifficulty::new));
        vals_s.put(PacketType.Play.Server.CLEAR_TITLES, fields(WrapperPlayServerClearTitles::new));
        vals_s.put(PacketType.Play.Server.TAB_COMPLETE, fields(WrapperPlayServerTabComplete::new));
        vals_s.put(PacketType.Play.Server.MULTI_BLOCK_CHANGE, fields(WrapperPlayServerMultiBlockChange::new));
        vals_s.put(PacketType.Play.Server.DECLARE_COMMANDS, fields(WrapperPlayServerDeclareCommands::new));
        vals_s.put(PacketType.Play.Server.CLOSE_WINDOW, fields(WrapperPlayServerCloseWindow::new));
        vals_s.put(PacketType.Play.Server.WINDOW_ITEMS, fields(WrapperPlayServerWindowItems::new));
        vals_s.put(PacketType.Play.Server.WINDOW_PROPERTY, fields(WrapperPlayServerWindowProperty::new));
        vals_s.put(PacketType.Play.Server.SET_SLOT, fields(WrapperPlayServerSetSlot::new));
        vals_s.put(PacketType.Play.Server.SET_COOLDOWN, fields(WrapperPlayServerSetCooldown::new));
        vals_s.put(PacketType.Play.Server.PLUGIN_MESSAGE, fields(WrapperPlayServerPluginMessage::new));
        vals_s.put(PacketType.Play.Server.DISCONNECT, fields(WrapperPlayServerDisconnect::new));
        vals_s.put(PacketType.Play.Server.ENTITY_STATUS, fields(WrapperPlayServerEntityStatus::new));
        vals_s.put(PacketType.Play.Server.EXPLOSION, fields(WrapperPlayServerExplosion::new));
        vals_s.put(PacketType.Play.Server.UNLOAD_CHUNK, fields(WrapperPlayServerUnloadChunk::new));
        vals_s.put(PacketType.Play.Server.CHANGE_GAME_STATE, fields(WrapperPlayServerChangeGameState::new));
        vals_s.put(PacketType.Play.Server.OPEN_HORSE_WINDOW, fields(WrapperPlayServerOpenHorseWindow::new));
        vals_s.put(PacketType.Play.Server.INITIALIZE_WORLD_BORDER, fields(WrapperPlayServerInitializeWorldBorder::new));
        vals_s.put(PacketType.Play.Server.KEEP_ALIVE, fields(WrapperPlayServerKeepAlive::new));
        vals_s.put(PacketType.Play.Server.CHUNK_DATA, fields(WrapperPlayServerChunkData::new));
        vals_s.put(PacketType.Play.Server.PARTICLE, fields(WrapperPlayServerParticle::new));
        vals_s.put(PacketType.Play.Server.UPDATE_LIGHT, fields(WrapperPlayServerUpdateLight::new));
        vals_s.put(PacketType.Play.Server.JOIN_GAME, fields(WrapperPlayServerJoinGame::new));
        vals_s.put(PacketType.Play.Server.MAP_DATA, fields(WrapperPlayServerMapData::new));
        vals_s.put(PacketType.Play.Server.MERCHANT_OFFERS, fields(WrapperPlayServerMerchantOffers::new));
        vals_s.put(PacketType.Play.Server.ENTITY_RELATIVE_MOVE, fields(WrapperPlayServerEntityRelativeMove::new));
        vals_s.put(PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION, fields(WrapperPlayServerEntityRelativeMoveAndRotation::new));
        vals_s.put(PacketType.Play.Server.ENTITY_ROTATION, fields(WrapperPlayServerEntityRotation::new));
        vals_s.put(PacketType.Play.Server.VEHICLE_MOVE, fields(WrapperPlayServerVehicleMove::new));
        vals_s.put(PacketType.Play.Server.OPEN_BOOK, fields(WrapperPlayServerOpenBook::new));
        vals_s.put(PacketType.Play.Server.OPEN_WINDOW, fields(WrapperPlayServerOpenWindow::new));
        vals_s.put(PacketType.Play.Server.OPEN_SIGN_EDITOR, fields(WrapperPlayServerOpenSignEditor::new));
        vals_s.put(PacketType.Play.Server.PING, fields(WrapperPlayServerPing::new));
        vals_s.put(PacketType.Play.Server.CRAFT_RECIPE_RESPONSE, fields(WrapperPlayServerCraftRecipeResponse::new));
        vals_s.put(PacketType.Play.Server.PLAYER_ABILITIES, fields(WrapperPlayServerPlayerAbilities::new));
        vals_s.put(PacketType.Play.Server.END_COMBAT_EVENT, (event, fields) -> {
            WrapperPlayServerEndCombatEvent wrapper = new WrapperPlayServerEndCombatEvent(event);
            fields.put("duration", wrapper.getDuration());
            wrapper.getEntityId().ifPresent(entityId -> fields.put("entityId", entityId));
        });
        vals_s.put(PacketType.Play.Server.ENTER_COMBAT_EVENT, null);
        vals_s.put(PacketType.Play.Server.DEATH_COMBAT_EVENT, (event, fields) -> {
            WrapperPlayServerDeathCombatEvent wrapper = new WrapperPlayServerDeathCombatEvent(event);
            fields.put("playerId", wrapper.getPlayerId());
            wrapper.getEntityId().ifPresent(entityId -> fields.put("entityId", entityId));
            fields.put("deathMessage", wrapper.getDeathMessage());
        });
        vals_s.put(PacketType.Play.Server.FACE_PLAYER, (event, fields) -> {
            WrapperPlayServerFacePlayer wrapper = new WrapperPlayServerFacePlayer(event);
            fields.put("aimUnit", wrapper.getAimUnit());
            Vector3d targetPosition = wrapper.getTargetPosition();
            fields.put("x", targetPosition.x);
            fields.put("y", targetPosition.y);
            fields.put("z", targetPosition.z);
            WrapperPlayServerFacePlayer.TargetEntity targetEntity = wrapper.getTargetEntity();
            fields.put("targetEntity", targetEntity == null ? null : "{entityId=" + targetEntity.getEntityId() + ", entitySection=" + targetEntity.getEntitySection() + "}");
        });
        vals_s.put(PacketType.Play.Server.PLAYER_POSITION_AND_LOOK, (event, fields) -> {
            WrapperPlayServerPlayerPositionAndLook wrapper = new WrapperPlayServerPlayerPositionAndLook(event);
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_9)) {
                fields.put("teleportId", wrapper.getTeleportId());
            }

            fields.put("position", MessageUtil.toUnlabledString(wrapper.getPosition()));
            fields.put("yaw", wrapper.getYaw());
            fields.put("pitch", wrapper.getPitch());

            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2)) {
                fields.put("velocity", MessageUtil.toUnlabledString(wrapper.getDeltaMovement()));
            }

            fields.put("flags", wrapper.getRelativeFlags().getFullMask());
            if (wrapper.getServerVersion().isNewerThanOrEquals(ServerVersion.V_1_17)
                    && wrapper.getServerVersion().isOlderThanOrEquals(ServerVersion.V_1_19_3)) {
                fields.put("dismountVehicle", wrapper.isDismountVehicle());
            }
        });
        /* TODO:
                UNLOCK_RECIPES,
                DESTROY_ENTITIES,
                REMOVE_ENTITY_EFFECT,
                RESOURCE_PACK_SEND,
                RESPAWN,
                ENTITY_HEAD_LOOK,
                SELECT_ADVANCEMENTS_TAB,
                ACTION_BAR,
                WORLD_BORDER_CENTER,
                WORLD_BORDER_LERP_SIZE,
                WORLD_BORDER_SIZE,
                WORLD_BORDER_WARNING_DELAY,
                WORLD_BORDER_WARNING_REACH,
                CAMERA,
                HELD_ITEM_CHANGE,
                UPDATE_VIEW_POSITION,
                UPDATE_VIEW_DISTANCE,
                SPAWN_POSITION,
                DISPLAY_SCOREBOARD,
                ENTITY_METADATA,
                ATTACH_ENTITY,
                ENTITY_VELOCITY,
                ENTITY_EQUIPMENT,
                SET_EXPERIENCE,
                UPDATE_HEALTH,
                SCOREBOARD_OBJECTIVE,
                SET_PASSENGERS,
                TEAMS,
                UPDATE_SCORE,
                UPDATE_SIMULATION_DISTANCE,
                SET_TITLE_SUBTITLE,
                TIME_UPDATE,
                SET_TITLE_TEXT,
                SET_TITLE_TIMES,
                ENTITY_SOUND_EFFECT,
                SOUND_EFFECT,
                STOP_SOUND,
                PLAYER_LIST_HEADER_AND_FOOTER,
                NBT_QUERY_RESPONSE,
                COLLECT_ITEM,
                ENTITY_TELEPORT,
                UPDATE_ADVANCEMENTS,
                UPDATE_ATTRIBUTES,
                ENTITY_EFFECT,
                DECLARE_RECIPES,
                TAGS,
                CHAT_MESSAGE,
                ACKNOWLEDGE_BLOCK_CHANGES,
                SERVER_DATA,
                SYSTEM_CHAT_MESSAGE,
                DELETE_CHAT,
                CUSTOM_CHAT_COMPLETIONS,
                DISGUISED_CHAT,
                PLAYER_INFO_REMOVE,
                PLAYER_INFO_UPDATE,
                DAMAGE_EVENT,
                HURT_ANIMATION,
                BUNDLE,
                CHUNK_BIOMES,
                CHUNK_BATCH_END,
                CHUNK_BATCH_BEGIN,
                DEBUG_PONG,
                CONFIGURATION_START,
                RESET_SCORE,
                RESOURCE_PACK_REMOVE,
                TICKING_STATE,
                TICKING_STEP,
                COOKIE_REQUEST,
                DEBUG_SAMPLE,
                STORE_COOKIE,
                TRANSFER,
                PROJECTILE_POWER,
                CUSTOM_REPORT_DETAILS,
                SERVER_LINKS,
                MOVE_MINECART,
                SET_CURSOR_ITEM,
                SET_PLAYER_INVENTORY,
                ENTITY_POSITION_SYNC,
                PLAYER_ROTATION,
                RECIPE_BOOK_ADD,
                RECIPE_BOOK_REMOVE,
                RECIPE_BOOK_SETTINGS
         */
    }

    static {
        initC();
        initS();
    }
}
