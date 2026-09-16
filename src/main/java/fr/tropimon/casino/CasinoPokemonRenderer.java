package fr.tropimon.casino;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Quaternionf;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Rendu des modèles et textures officiels chargés par Cobblemon. */
final class CasinoPokemonRenderer {
    private static final Method PROFILE_RENDERER = findProfileRenderer();
    private static final Map<SlotSymbol, Portrait> PORTRAITS = new EnumMap<>(SlotSymbol.class);

    private CasinoPokemonRenderer() {}

    static boolean draw(DrawContext context, SlotSymbol symbol, int x, int y, int size) {
        String speciesId = switch (symbol) {
            case PSYDUCK -> "psyduck";
            case SLOWPOKE -> "slowpoke";
            case MEOWTH -> "meowth";
            case GENGAR -> "gengar";
            default -> null;
        };
        if (speciesId == null) return false;
        boolean pushed = false;
        try {
            Portrait portrait = PORTRAITS.computeIfAbsent(symbol, ignored -> create(speciesId));
            if (portrait == null) return false;
            context.getMatrices().push();
            pushed = true;
            float scale = size / 44.0F;
            context.getMatrices().translate(x + size / 2.0D, y, 1000.0D);
            context.getMatrices().scale(scale, scale, scale);
            Quaternionf rotation = portrait.rotation.rotationXYZ(
                    (float) Math.toRadians(13.0D), (float) Math.toRadians(25.0D), 0.0F);
            MinecraftClient client = MinecraftClient.getInstance();
            float frameTicks = client.isPaused() ? 0.0F
                    : Math.clamp(client.getRenderTickCounter().getLastFrameDuration(), 0.0F, 2.0F);
            drawProfilePokemon(portrait, context, rotation, frameTicks);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            if (pushed) context.getMatrices().pop();
        }
    }

    private static void drawProfilePokemon(Portrait portrait, DrawContext context,
                                           Quaternionf rotation, float frameTicks) {
        try {
            if (PROFILE_RENDERER.getParameterCount() == 16) {
                Class<?> transformType =
                        Class.forName("com.cobblemon.mod.common.client.gui.ProfileTransformType");
                Object profileTransform = transformType.getField("PROFILE").get(null);
                PROFILE_RENDERER.invoke(null, portrait.pokemon, context.getMatrices(), rotation,
                        PoseType.PROFILE, portrait.state, frameTicks, 20.0F, profileTransform, true,
                        1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 15);
            } else {
                PROFILE_RENDERER.invoke(null, portrait.pokemon, context.getMatrices(), rotation,
                        PoseType.PROFILE, portrait.state, frameTicks, 20.0F, true, false,
                        1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("API de portrait Cobblemon non compatible", exception);
        }
    }

    private static Method findProfileRenderer() {
        for (Method method : PokemonGuiUtilsKt.class.getMethods()) {
            if (method.getName().equals("drawProfilePokemon")
                    && method.getParameterCount() >= 15
                    && method.getParameterTypes()[0] == RenderablePokemon.class) {
                return method;
            }
        }
        throw new IllegalStateException("Rendu de portrait Cobblemon introuvable");
    }

    private static Portrait create(String speciesId) {
        var species = PokemonSpecies.getByName(speciesId);
        if (species == null) return null;
        Set<String> aspects = Set.of();
        FloatingState state = new FloatingState();
        state.setCurrentAspects(aspects);
        return new Portrait(new RenderablePokemon(species, aspects, ItemStack.EMPTY), state, new Quaternionf());
    }

    private static final class Portrait {
        private final RenderablePokemon pokemon;
        private final FloatingState state;
        private final Quaternionf rotation;

        private Portrait(RenderablePokemon pokemon, FloatingState state, Quaternionf rotation) {
            this.pokemon = pokemon;
            this.state = state;
            this.rotation = rotation;
        }
    }
}
