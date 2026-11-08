package com.coolfreeze23.headinajar.compat;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.block.HeadJobs;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.host.Hosts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade: mecha power, heat and mass; host freshness and sync; whose body this is; genes carried; heads on the job. */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {
    static final ResourceLocation MECHA = HeadInAJar.id("mecha"), HOST = HeadInAJar.id("host"), BODY = HeadInAJar.id("body"),
            GENES = HeadInAJar.id("genes"), HEADS = HeadInAJar.id("heads");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new Mecha(), MechaEntity.class);
        registration.registerEntityComponent(new Host(), Mob.class);
        registration.registerEntityComponent(new Body(), HeadlessBody.class);
        registration.registerEntityComponent(new Genes(), LivingEntity.class);
        registration.registerBlockComponent(new Heads(), Block.class);
    }

    static class Mecha implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof MechaEntity m)) return;
            tooltip.add(Component.translatable("jade.headinajar.mecha", m.battery() / 1000, Math.round(100 * m.heat() / m.frame().heatCap), m.mass(), m.frame().load));
        }

        @Override
        public ResourceLocation getUid() {
            return MECHA;
        }
    }

    static class Host implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof Mob mob) || mob instanceof MechaEntity || !Hosts.isHost(mob)) return;
            var data = Hosts.data(mob);
            if (data.shell) return;
            tooltip.add(Component.translatable("jade.headinajar.host", Math.round(data.freshness), Math.round(data.effectiveSync())).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return HOST;
        }
    }

    static class Body implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof HeadlessBody body) || body.ownerName() == null) return;
            tooltip.add(Component.translatable(body.isCorpse() ? "jade.headinajar.corpse" : "jade.headinajar.body", body.ownerName()).withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return BODY;
        }
    }

    static class Genes implements IEntityComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof LivingEntity e)) return;
            var set = Genetics.genes(e);
            if (set.isEmpty()) return;
            StringBuilder line = new StringBuilder();
            for (Gene g : set.strengths().keySet()) {
                if (!line.isEmpty()) line.append(", ");
                line.append(Component.translatable("gene.headinajar." + g.id()).getString());
            }
            tooltip.add(Component.translatable("jade.headinajar.genes", line.toString()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        @Override
        public ResourceLocation getUid() {
            return GENES;
        }
    }

    /** The head on a turret, mount or socket. */
    static class Heads implements IBlockComponentProvider {
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof com.coolfreeze23.headinajar.block.HasItems holder)) return;
            if (!(holder instanceof com.coolfreeze23.headinajar.block.HeadHolderBlock.Holder)) return;
            var head = holder.items().getStackInSlot(0);
            if (head.isEmpty()) return;
            String job = HeadJobs.profession(head);
            tooltip.add(job.isEmpty() ? head.getHoverName()
                    : Component.translatable("jade.headinajar.staff", Component.translatable("entity.minecraft.villager." + job)));
        }

        @Override
        public ResourceLocation getUid() {
            return HEADS;
        }
    }
}
