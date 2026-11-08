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

    public static void reveal() {
        HIDDEN.clear();
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
}
