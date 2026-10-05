package com.ascensionlib.battle;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@link ShowdownHost} on top of CobbleRaids' {@code ShowdownExtensions} API, reached by reflection: CobbleRaids is
 * optional for the library, and a compile-time dependency would make it a required artifact for every build.
 *
 * <p>The contract is two static methods ({@code registerModule(String, Supplier)} and
 * {@code registerFormatFields(FormatFieldProvider)}) and that provider interface. When CobbleRaids is absent, or its API is
 * not what this expects, {@link #find()} says so once and returns empty; the library then runs with battle effects off.
 */
final class RaidsShowdownHost implements ShowdownHost {
    private static final String MOD_ID = "cobbleraids";
    private static final String API = "com.cobbleraids.api.showdown.ShowdownExtensions";
    private static final String PROVIDER = API + "$FormatFieldProvider";
    /** The extension API version this was written against. */
    private static final int SUPPORTED_API_VERSION = 1;

    private final Method registerModule;
    private final Method registerFormatFields;
    private final Class<?> providerType;

    private RaidsShowdownHost(Method registerModule, Method registerFormatFields, Class<?> providerType) {
        this.registerModule = registerModule;
        this.registerFormatFields = registerFormatFields;
        this.providerType = providerType;
    }

    /** The host, or empty (with the reason on {@code System.err}, as the logger may not be usable this early). */
    static Optional<ShowdownHost> find() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            System.err.println("[AscensionLib] CobbleRaids is not installed: ascension effects will not act in battle.");
            return Optional.empty();
        }
        try {
            Class<?> api = Class.forName(API);
            Class<?> provider = Class.forName(PROVIDER);
            int version = api.getField("API_VERSION").getInt(null);
            if (version != SUPPORTED_API_VERSION) {
                System.err.println("[AscensionLib] CobbleRaids' Showdown extension API is version " + version
                        + ", not " + SUPPORTED_API_VERSION + ": ascension effects will not act in battle.");
                return Optional.empty();
            }
            return Optional.of(new RaidsShowdownHost(api.getMethod("registerModule", String.class, Supplier.class),
                    api.getMethod("registerFormatFields", provider), provider));
        } catch (ReflectiveOperationException | LinkageError ex) {
            System.err.println("[AscensionLib] CobbleRaids is installed but its Showdown extension API could not be "
                    + "reached (" + ex + "): ascension effects will not act in battle.");
            return Optional.empty();
        }
    }

    @Override public String name() {
        return "CobbleRaids' Showdown extension API";
    }

    @Override public void registerModule(String id, Supplier<InputStream> source) {
        call(registerModule, id, source);
    }

    @Override public void registerFormatFields(FieldProvider provider) {
        Object proxy = Proxy.newProxyInstance(providerType.getClassLoader(), new Class<?>[] {providerType},
                (self, method, args) -> {
                    if (method.getName().equals("fieldsFor") && args != null && args.length == 2) {
                        @SuppressWarnings("unchecked") List<UUID> players = (List<UUID>) args[1];
                        return provider.fieldsFor((UUID) args[0], players);
                    }
                    return switch (method.getName()) {
                        case "toString" -> "AscensionLib format-field provider";
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> null;
                    };
                });
        call(registerFormatFields, proxy);
    }

    private static void call(Method method, Object... args) {
        try {
            method.invoke(null, args);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException(ex.getCause());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
