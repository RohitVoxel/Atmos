package net.atmos.ui.util;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reusable UI component factory for Atmos categories.
 * Uses standard YACL v3 API (dev.isxander.yacl3).
 */
public final class CategoryHelper {

    private CategoryHelper() {}

    /** Creates a boolean toggle. */
    public static Option<Boolean> toggle(String name, String tooltip,
                                         boolean defaultVal,
                                         Supplier<Boolean> getter,
                                         Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tooltip)))
                .binding(defaultVal, getter, setter)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    /** Creates a float slider with custom formatter. */
    public static Option<Float> floatSlider(String name, String tooltip,
                                            float defaultVal, float min, float max, float step,
                                            Supplier<Float> getter,
                                            Consumer<Float> setter,
                                            Function<Float, String> formatter) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tooltip)))
                .binding(defaultVal, getter, setter)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(min, max)
                        .step(step)
                        .formatValue(v -> Component.literal(formatter.apply(v))))
                .build();
    }

    /** Creates an Option Group builder. */
    public static OptionGroup.Builder group(String name, String tooltip) {
        return OptionGroup.createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tooltip)));
    }
}