package ac.grim.grimac.checks;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.api.storage.verbose.Verbose;
import ac.grim.grimac.api.storage.verbose.VerboseBuf;
import ac.grim.grimac.api.storage.verbose.VerboseRenderContext;
import ac.grim.grimac.internal.storage.verbose.VerboseRegistry;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.math.GrimMath;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

// Class from https://github.com/Tecnio/AntiCheatBase/blob/master/src/main/java/me/tecnio/anticheat/check/Check.java
@Getter
public class Check extends GrimProcessor implements AbstractCheck {
    private static final FlagEvent.Channel FLAG_CHANNEL = GrimAPI.INSTANCE.getEventBus().get(FlagEvent.class);
    private static final ThreadLocal<VerboseBuf> VERBOSE = ThreadLocal.withInitial(VerboseBuf::new);

    private long lastViolationTime;
    private final VerboseBuf verbose = VERBOSE.get();

    // the JVM spec doesn't guarantee that this is cached, but it usually is
    private final @NotNull CheckData checkData;

    // configurable
    private @NotNull String displayName;
    private @NotNull String description;
    private int setbackVL;
    @Setter private boolean isEnabled;

    // permissions
    private boolean exemptPermission;
    private boolean noSetbackPermission;
    private boolean noModifyPacketPermission;

    public Check(final @NotNull GrimPlayer player) {
        super(player, false);

        this.checkData = this.getClass().getAnnotation(CheckData.class);
        if (checkData == null) {
            throw new IllegalStateException("Check class must have @CheckData annotation. Consider using GrimProcessor instead.");
        }

        this.description = this.getDefaultDescription();
        this.displayName = this.getCheckName();

        reload();
    }

    @Contract(pure = true)
    @Override
    public final @NotNull String getCheckName() {
        return checkData.name();
    }

    @Contract(pure = true)
    @Override
    public @NotNull String getConfigName() {
        String configName = checkData.configName();
        return configName.equals("DEFAULT") ? this.getCheckName() : configName;
    }

    @Contract(pure = true)
    @Override
    public final @NotNull String getAlternativeName() {
        return checkData.alternativeName();
    }

    @Contract(pure = true)
    @Override
    public final @NotNull String getStableKey() {
        return checkData.stableKey();
    }

    @Contract(pure = true)
    @Override
    public final boolean isExperimental() {
        return checkData.experimental();
    }

    @Contract(pure = true)
    public final @NotNull String getDefaultDescription() {
        return checkData.description();
    }

    @Contract(pure = true)
    public final int getDefaultSetbackVL() {
        return checkData.setback();
    }

    // must be `double` because AbstractCheck expects it
    @Contract(pure = true)
    @Override
    public double getViolations() {
        return player.punishmentManager.getViolations(this);
    }

    // must be `double` because AbstractCheck expects it
    @Contract(pure = true)
    @Override
    public double getSetbackVL() {
        return setbackVL;
    }

    @Contract(pure = true)
    @Deprecated(forRemoval = true)
    @Override
    public double getDecay() {
        return 0;
    }

    public boolean shouldModifyPackets() {
        return isEnabled
                && !player.disableGrim
                && !player.noModifyPacketPermission
                && !noModifyPacketPermission
                && !exemptPermission;
    }

    /**
     * Evaluated once when CheckManager builds the dispatch arrays.
     * Implementations must only depend on immutable connection properties.
     */
    public boolean isApplicable() {
        return true;
    }

    public final void updatePermissions() {
        final String id = getConfigName().toLowerCase();
        exemptPermission = player.hasPermission("grim.exempt." + id);
        noSetbackPermission = player.hasPermission("grim.nosetback." + id);
        noModifyPacketPermission = player.hasPermission("grim.nomodifypacket." + id);
    }

    public final boolean flag() {
        return flag("");
    }

    public final boolean flag(String verbose) {
        Supplier<String> alertText = constant(verbose);
        if (recordFlag(alertText)) {
            alert(alertText);
            return true;
        }
        return false;
    }

    public final boolean flag(@NotNull Verbose.Writer verbose) {
        BinaryVerbose binary = lazyVerbose(verbose);
        if (recordFlag(binary)) {
            alert(binary.rendered());
            return true;
        }
        return false;
    }

    public final boolean flag(@NotNull Verbose.Writer verbose, @NotNull Supplier<String> alertText) {
        BinaryVerbose binary = lazyVerbose(verbose);
        if (recordFlag(binary)) {
            alert(memoize(Objects.requireNonNull(alertText, "alertText")));
            return true;
        }
        return false;
    }

    private boolean recordFlag(@NotNull Supplier<String> verbose) {
        if (player.disableGrim || (isExperimental() && !player.isExperimentalChecks()) || exemptPermission)
            return false; // Avoid calling event if disabled

        if (FLAG_CHANNEL.fire(player, this, verbose)) return false;

        player.punishmentManager.handleViolation(this);
        lastViolationTime = System.currentTimeMillis();

        String v = verbose.get();
        String renderedVerbose = v == null ? "" : v;
        String verboseWithoutGl = renderedVerbose.replaceAll(" /gl .*", "");
        GrimAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                .recordFlagFromCheck(player, this, getViolations(), verboseWithoutGl);
        return true;
    }

    private boolean recordFlag(@NotNull BinaryVerbose verbose) {
        if (player.disableGrim || (isExperimental() && !player.isExperimentalChecks()) || exemptPermission)
            return false; // Avoid calling event if disabled

        Supplier<String> rendered = verbose.rendered();
        byte[] verboseData = verbose.data();

        if (FLAG_CHANNEL.fire(player, this, rendered)) return false;

        player.punishmentManager.handleViolation(this);
        lastViolationTime = System.currentTimeMillis();
        GrimAPI.INSTANCE.getDataStoreLifecycle().liveWriteHooks()
                .recordFlagDataFromCheck(player, this, getViolations(), verboseData);
        return true;
    }

    private @NotNull BinaryVerbose lazyVerbose(@NotNull Verbose.Writer writer) {
        Objects.requireNonNull(writer, "writer");
        byte[] verboseData = writer.end().toByteArray();
        Verbose template = writer.verbose();
        Supplier<String> rendered = memoize(() -> template.render(verboseData, new VerboseRenderContext(
                player.getClientVersion().getProtocolVersion(),
                GrimAPI.INSTANCE.getPlatformServer().getPlatformImplementationString())));
        return new BinaryVerbose(verboseData, rendered);
    }

    public final void registerVerboseTemplates(@Nullable VerboseRegistry registry) {
        if (registry == null || getStableKey().isEmpty()) return;
        String pluginVersion = GrimAPI.INSTANCE.getExternalAPI().getGrimVersion();
        for (Verbose template : Verbose.declaredBy(getClass(), Check.class)) {
            registry.registerTemplate(this.getStableKey(), this.getCheckName(), description, pluginVersion, template);
        }
    }

    protected final @NotNull VerboseBuf verbose() {
        return verbose;
    }

    public final boolean flagWithSetback() {
        return flagWithSetback("");
    }

    public final boolean flagWithSetback(String verbose) {
        if (flag(verbose)) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final boolean flagWithSetback(@NotNull Verbose.Writer verbose) {
        if (flag(verbose)) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    public final boolean flagWithSetback(@NotNull Verbose.Writer verbose, @NotNull Supplier<String> alertText) {
        if (flag(verbose, alertText)) {
            setbackIfAboveSetbackVL();
            return true;
        }
        return false;
    }

    @Override
    public final void reload(@NotNull ConfigManager configuration) {
        setbackVL = GrimMath.ceil(configuration.getDoubleElse(getConfigName() + ".setbackvl", getDefaultSetbackVL()));
        displayName = configuration.getStringElse(getConfigName() + ".displayname", getCheckName());
        description = configuration.getStringElse(getConfigName() + ".description", getDefaultDescription());

        if (setbackVL == -1) setbackVL = Integer.MAX_VALUE;
        super.reload(configuration);
    }

    public boolean alert(String verbose) {
        return alert(constant(verbose));
    }

    public boolean alert(@NotNull Supplier<String> verbose) {
        return player.punishmentManager.handleAlert(player, memoize(Objects.requireNonNull(verbose, "verbose")), this);
    }

    public boolean setbackIfAboveSetbackVL() {
        if (shouldSetback()) {
            return player.getSetbackTeleportUtil().executeViolationSetback();
        }
        return false;
    }

    public boolean shouldSetback() {
        return !noSetbackPermission && getViolations() > setbackVL;
    }

    public boolean executeViolationSetback() {
        return !noSetbackPermission && player.getSetbackTeleportUtil().executeViolationSetback();
    }

    public String formatOffset(double offset) {
        return offset > 0.001 ? String.format("%.5f", offset) : String.format("%.2E", offset);
    }

    private static @NotNull Supplier<String> constant(String verbose) {
        String value = verbose == null ? "" : verbose;
        return () -> value;
    }

    private static @NotNull Supplier<String> memoize(@NotNull Supplier<String> supplier) {
        return new Supplier<>() {
            private String value;
            private boolean computed;

            @Override
            public synchronized String get() {
                if (!computed) {
                    try {
                        value = supplier.get();
                        if (value == null) value = "";
                    } catch (RuntimeException ignored) {
                        value = "";
                    }
                    computed = true;
                }
                return value;
            }
        };
    }

    private record BinaryVerbose(byte @NotNull [] data, @NotNull Supplier<String> rendered) {}
}
