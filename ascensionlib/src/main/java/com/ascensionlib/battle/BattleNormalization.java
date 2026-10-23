package com.ascensionlib.battle;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cobblemon.mod.common.Cobblemon;
import java.util.concurrent.CompletableFuture;
import kotlin.Unit;
import java.nio.charset.StandardCharsets;

/** Explicit battle-scoped opt-out for integrations that supply their own normalized combat rules. */
public final class BattleNormalization {
    private static final int LIMIT = 1024;
    private static final Map<UUID, UUID> OWNERS = new HashMap<>();
    private static final Map<UUID, String> PAYLOADS = new HashMap<>();

    private BattleNormalization() {}

    public static boolean isSimulatorAvailable() { return BattleFxInstall.normalizationRegistered(); }

    /** Values are level, HP, attack, defence, special attack, special defence, speed. Include both sides. */
    public static synchronized void normalize(UUID battleId, UUID owner, Map<UUID, List<Integer>> combatants) {
        if (!isSimulatorAvailable()) throw new IllegalStateException("No normalization simulator host registered");
        if (combatants.isEmpty() || combatants.size() > 24) throw new IllegalArgumentException("Invalid normalized battle size");
        var root = new JsonObject(); root.addProperty("v", 1);
        root.addProperty("battle", battleId.toString());
        var mons = new JsonObject();
        combatants.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Objects.requireNonNull(entry.getKey());
            var values = List.copyOf(entry.getValue());
            if (values.size() != 7 || values.get(0) < 1 || values.get(0) > 100)
                throw new IllegalArgumentException("Invalid normalized level");
            for (int index = 1; index < 7; index++) if (values.get(index) < 1 || values.get(index) > 10000)
                throw new IllegalArgumentException("Invalid normalized stat");
            var array = new JsonArray(); values.forEach(array::add); mons.add(entry.getKey().toString(), array);
        });
        root.add("mons", mons); String payload = root.toString();
        String existing = PAYLOADS.get(battleId);
        if (existing != null && !existing.equals(payload)) throw new IllegalStateException("Normalized battle payload is immutable");
        suppress(battleId, owner); PAYLOADS.put(battleId, payload);
    }

    /** Runs on Cobblemon's simulator thread. Registration alone is not a runtime acknowledgement. */
    public static CompletableFuture<Void> verifyAndAcknowledge(UUID battleId,UUID owner) {
        final String expected;
        synchronized(BattleNormalization.class) {
            if(!owner.equals(OWNERS.get(battleId))||(expected=PAYLOADS.get(battleId))==null)
                return CompletableFuture.failedFuture(new IllegalStateException("No owned normalized battle payload"));
        }
        var result=new CompletableFuture<Void>();
        Cobblemon.INSTANCE.getShowdownThread().queue(service->{
            try {
                // Public Graal context access is optional; a socket simulator requires its own acknowledged adapter.
                var context=service.getClass().getMethod("getContext").invoke(service);
                var bindings=context.getClass().getMethod("getBindings",String.class).invoke(context,"js");
                var getMember=bindings.getClass().getMethod("getMember",String.class);
                var verify=getMember.invoke(bindings,"ascensionNormalizationVerify");
                if(verify==null)throw new IllegalStateException("Normalization simulator module is unavailable");
                var execute=verify.getClass().getMethod("execute",Object[].class);
                var value=execute.invoke(verify,(Object)new Object[]{battleId.toString()});
                String actual=(String)value.getClass().getMethod("asString").invoke(value);
                if(actual.length()>16384||!JsonParser.parseString(expected).equals(JsonParser.parseString(actual)))
                    throw new IllegalStateException("Simulator normalization acknowledgement differs from native manifest");
                synchronized(BattleNormalization.class) {
                    if(!owner.equals(OWNERS.get(battleId))||!expected.equals(PAYLOADS.get(battleId)))
                        throw new IllegalStateException("Normalization scope ended before simulator acknowledgement");
                }
                if(getMember.invoke(bindings,"ascensionNormalizationOutcome")==null)throw new IllegalStateException("Normalization exhaustion checkpoint support is unavailable");
                if(getMember.invoke(bindings,"ascensionNormalizationReleaseCheckpoint")==null)throw new IllegalStateException("Complete checkpoint support is unavailable");
                var acknowledge=getMember.invoke(bindings,"ascensionNormalizationAcknowledge");
                if(acknowledge==null)throw new IllegalStateException("Normalization acknowledgement gate is unavailable");
                execute.invoke(acknowledge,(Object)new Object[]{battleId.toString()});
                result.complete(null);
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
            return Unit.INSTANCE;
        });
        return result;
    }

    /** Inspect only the owned simulator; ACTIVE, PLAYER_WIPE and DOUBLE_WIPE are exhaustive. */
    public static CompletableFuture<String> outcome(UUID battleId,UUID owner) {
        synchronized(BattleNormalization.class) {
            if(!owner.equals(OWNERS.get(battleId))||!PAYLOADS.containsKey(battleId))return CompletableFuture.failedFuture(new IllegalStateException("No owned normalized outcome scope"));
        }
        var result=new CompletableFuture<String>();
        Cobblemon.INSTANCE.getShowdownThread().queue(service->{
            try {
                var context=service.getClass().getMethod("getContext").invoke(service);
                var bindings=context.getClass().getMethod("getBindings",String.class).invoke(context,"js");
                var function=bindings.getClass().getMethod("getMember",String.class).invoke(bindings,"ascensionNormalizationOutcome");
                if(function==null)throw new IllegalStateException("Normalization exhaustion checkpoint support is unavailable");
                synchronized(BattleNormalization.class) {if(!owner.equals(OWNERS.get(battleId)))throw new IllegalStateException("Normalization scope ended before outcome inspection");}
                var value=function.getClass().getMethod("execute",Object[].class).invoke(function,(Object)new Object[]{battleId.toString()});
                String status=(String)value.getClass().getMethod("asString").invoke(value);
                if(!status.equals("ACTIVE")&&!status.equals("PLAYER_WIPE")&&!status.equals("DOUBLE_WIPE"))throw new IllegalStateException("Unknown normalized outcome");
                result.complete(status);
            }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
            return Unit.INSTANCE;
        });return result;
    }

    /** Freeze simulator choices and serialize the exact state on its owning thread before native shutdown. */
    public static CompletableFuture<byte[]> capture(UUID battleId,UUID owner) {return capture(battleId,owner,false);}
    public static CompletableFuture<byte[]> captureForCheckpoint(UUID battleId,UUID owner) {return capture(battleId,owner,true);}
    private static CompletableFuture<byte[]> capture(UUID battleId,UUID owner,boolean periodic) {
        synchronized(BattleNormalization.class) {
            if(!owner.equals(OWNERS.get(battleId))||!PAYLOADS.containsKey(battleId))
                return CompletableFuture.failedFuture(new IllegalStateException("No owned normalized battle to capture"));
        }
        var result=new CompletableFuture<byte[]>();
        Cobblemon.INSTANCE.getShowdownThread().queue(service->{
            try {
                var context=service.getClass().getMethod("getContext").invoke(service);
                var bindings=context.getClass().getMethod("getBindings",String.class).invoke(context,"js");
                var function=bindings.getClass().getMethod("getMember",String.class).invoke(bindings,"ascensionNormalizationCapture");
                if(function==null)throw new IllegalStateException("Normalization checkpoint module is unavailable");
                synchronized(BattleNormalization.class) {
                    if(!owner.equals(OWNERS.get(battleId)))throw new IllegalStateException("Normalization scope ended before capture");
                }
                var value=function.getClass().getMethod("execute",Object[].class).invoke(function,(Object)new Object[]{battleId.toString(),periodic});
                String json=(String)value.getClass().getMethod("asString").invoke(value);
                if(json.length()>1_048_576)throw new IllegalStateException("Simulator checkpoint exceeds size limit");
                byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>1_048_576)throw new IllegalStateException("Simulator checkpoint exceeds byte limit");
                result.complete(bytes);
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
            return Unit.INSTANCE;
        });
        return result;
    }
    /** Release only the exact owned periodic capture, replaying choices buffered after its boundary. */
    public static CompletableFuture<Void> releaseCheckpoint(UUID battleId,UUID owner,byte[] checkpoint) {
        if(checkpoint.length==0||checkpoint.length>1048576)return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid checkpoint token"));
        String token=new String(checkpoint,StandardCharsets.UTF_8);
        synchronized(BattleNormalization.class){if(!owner.equals(OWNERS.get(battleId)))return CompletableFuture.failedFuture(new IllegalStateException("No owned checkpoint scope"));}
        var result=new CompletableFuture<Void>();
        Cobblemon.INSTANCE.getShowdownThread().queue(service->{
            try {
                var context=service.getClass().getMethod("getContext").invoke(service);
                var bindings=context.getClass().getMethod("getBindings",String.class).invoke(context,"js");
                var function=bindings.getClass().getMethod("getMember",String.class).invoke(bindings,"ascensionNormalizationReleaseCheckpoint");
                if(function==null)throw new IllegalStateException("Periodic checkpoint release is unavailable");
                synchronized(BattleNormalization.class){if(!owner.equals(OWNERS.get(battleId)))throw new IllegalStateException("Checkpoint scope ended");}
                var value=function.getClass().getMethod("execute",Object[].class).invoke(function,(Object)new Object[]{battleId.toString(),token});
                if(!"RESUMED".equals(value.getClass().getMethod("asString").invoke(value)))throw new IllegalStateException("Checkpoint gate did not acknowledge release");
                result.complete(null);
            }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
            return Unit.INSTANCE;
        });return result;
    }
    /** Restore behind the closed choice gate; the caller must apply the returned native view before acknowledging. */
    public static CompletableFuture<byte[]> restore(UUID battleId,UUID owner,byte[] checkpoint,boolean handoff) {
        if(checkpoint.length==0||checkpoint.length>1_048_576)return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid continuation size"));
        final String json=new String(checkpoint,StandardCharsets.UTF_8);
        synchronized(BattleNormalization.class) {
            if(!owner.equals(OWNERS.get(battleId))||!PAYLOADS.containsKey(battleId))
                return CompletableFuture.failedFuture(new IllegalStateException("No owned normalized battle to restore"));
        }
        var result=new CompletableFuture<byte[]>();
        Cobblemon.INSTANCE.getShowdownThread().queue(service->{
            try {
                var context=service.getClass().getMethod("getContext").invoke(service);
                var bindings=context.getClass().getMethod("getBindings",String.class).invoke(context,"js");
                var function=bindings.getClass().getMethod("getMember",String.class).invoke(bindings,"ascensionNormalizationRestore");
                if(function==null)throw new IllegalStateException("Normalization continuation module is unavailable");
                synchronized(BattleNormalization.class) {
                    if(!owner.equals(OWNERS.get(battleId)))throw new IllegalStateException("Normalization scope ended before restore");
                }
                var value=function.getClass().getMethod("execute",Object[].class).invoke(function,(Object)new Object[]{battleId.toString(),json,handoff});
                String summary=(String)value.getClass().getMethod("asString").invoke(value);
                if(summary.length()>262144)throw new IllegalStateException("Native continuation view exceeds size limit");
                byte[] bytes=summary.getBytes(StandardCharsets.UTF_8);
                if(bytes.length>262144)throw new IllegalStateException("Native continuation view exceeds byte limit");
                result.complete(bytes);
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure){result.completeExceptionally(failure);}
            return Unit.INSTANCE;
        });
        return result;
    }

    static synchronized Map<String, String> fieldsFor(UUID battleId) {
        String payload = PAYLOADS.get(battleId);
        return payload == null ? Map.of() : Map.of("ascensionNormalization", payload);
    }

    /** Register before simulator fields are generated. A conflicting owner is never overwritten. */
    public static synchronized void suppress(UUID battleId, UUID owner) {
        Objects.requireNonNull(battleId, "battleId");
        Objects.requireNonNull(owner, "owner");
        UUID existing = OWNERS.get(battleId);
        if (existing != null && !existing.equals(owner)) throw new IllegalStateException("Battle normalization already owned");
        if (existing == null && OWNERS.size() >= LIMIT) throw new IllegalStateException("Battle normalization capacity reached");
        OWNERS.put(battleId, owner);
    }

    public static synchronized boolean isSuppressed(UUID battleId) {
        return OWNERS.containsKey(battleId);
    }

    /** Call only after simulator termination; mismatched integrations cannot release another owner's scope. */
    public static synchronized void release(UUID battleId, UUID owner) {
        Objects.requireNonNull(battleId, "battleId");
        Objects.requireNonNull(owner, "owner");
        UUID existing = OWNERS.get(battleId);
        if (existing != null && !existing.equals(owner)) throw new IllegalStateException("Battle normalization owner mismatch");
        OWNERS.remove(battleId);
        PAYLOADS.remove(battleId);
    }
}
