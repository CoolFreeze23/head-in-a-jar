package com.coolfreeze23.headinajar.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Finds the head part of any entity model (a HeadedModel's head, a part named "head", or a field called head). */
public final class HostHeads {
    private static final Set<ModelPart> HIDDEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Class<?>, Optional<Field>> FIELDS = new HashMap<>();

    private HostHeads() {
    }

    public static boolean hidden(ModelPart part) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(part);
    }

    public static void hide(EntityModel<?> model) {
        ModelPart head = find(model);
        if (head != null) HIDDEN.add(head);
        if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) HIDDEN.add(humanoid.hat);
    }

    /** Skips one more part while this host draws (a collared host's Hard Hat, worn on its jar's lid instead: 0.7.1). */
    public static void hidePart(ModelPart part) {
        if (part != null) HIDDEN.add(part);
    }

    public static void reveal() {
        HIDDEN.clear();
        ARMS.clear();
    }

    /** Arm angles to add to a part when it next draws in this render (after its model has posed it). */
    private static final Map<ModelPart, float[]> ARMS = new IdentityHashMap<>();

    /** Adds {@code part}'s angles to the model's own the next time it draws in this entity's render. */
    public static void poseArm(ModelPart part, float xRot, float yRot, float zRot) {
        if (part != null) ARMS.put(part, new float[]{xRot, yRot, zRot});
    }

    /** Called as a part starts to draw: applies a pending arm pose, once. */
    public static void applyArm(ModelPart part) {
        if (ARMS.isEmpty()) return;
        float[] a = ARMS.remove(part);
        if (a == null) return;
        part.xRot += a[0];
        part.yRot += a[1];
        part.zRot += a[2];
    }

    public static ModelPart find(EntityModel<?> model) {
        if (model instanceof HeadedModel headed) return headed.getHead();
        if (model instanceof HierarchicalModel<?> hierarchical) {
            Optional<ModelPart> head = hierarchical.getAnyDescendantWithName("head");
            if (head.isPresent()) return head.get();
        }
        Optional<Field> field = FIELDS.computeIfAbsent(model.getClass(), HostHeads::headField);
        if (field.isPresent()) {
            try {
                return (ModelPart) field.get().get(model);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private static Optional<Field> headField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == ModelPart.class && (f.getName().equals("head") || f.getName().equals("headParts"))) {
                    try {
                        f.setAccessible(true);
                        return Optional.of(f);
                    } catch (RuntimeException e) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Each layer class's own model fields (none for the layers that draw with the body's model). */
    private static final Map<Class<?>, java.util.List<Field>> LAYER_MODELS = new HashMap<>();
    private static Field layersField;
    private static boolean layersLooked;

    /**
     * A host's layers that draw a model of their own over the body (a drowned's or a stray's overlay, a sheep's wool, a
     * horse's or llama's gear): their heads are skipped too, or a headless drowned or stray showed its overlay's head
     * above the neck (seen at the empty collar in 0.7.1). Armour is left as it always was.
     */
    public static void hideLayers(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> renderer) {
        java.util.List<?> layers = layers(renderer);
        if (layers == null) return;
        for (Object layer : layers) {
            if (layer instanceof net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<?, ?, ?>) continue;
            for (Field f : LAYER_MODELS.computeIfAbsent(layer.getClass(), HostHeads::modelFields)) {
                try {
                    if (f.get(layer) instanceof EntityModel<?> model && model != renderer.getModel()) hide(model);
                } catch (IllegalAccessException e) {
                    // not readable: that layer draws as before
                }
            }
        }
    }

    private static java.util.List<?> layers(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> renderer) {
        if (!layersLooked) {
            layersLooked = true;
            try {
                layersField = net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredField("layers");
                layersField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                layersField = null;
            }
        }
        if (layersField == null) return null;
        try {
            return (java.util.List<?>) layersField.get(renderer);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static java.util.List<Field> modelFields(Class<?> type) {
        java.util.List<Field> out = new java.util.ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !EntityModel.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    out.add(f);
                } catch (RuntimeException e) {
                    // skipped
                }
            }
        }
        return out;
    }
}
