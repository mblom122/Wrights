package com.wrights.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.wrights.drone.Loadout;
import com.wrights.drone.PartSlot;
import com.wrights.drone.Swarm;
import com.wrights.drone.WrightTier;

/**
 * A wright stored as an item. It keeps its fitted parts ({@link Loadout}) and its name, so it comes back out
 * of a wrightport exactly as it went in.
 */
public class WrightItem extends Item {
    public WrightItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(Loadout parts, String name, int count) {
        ItemStack stack = new ItemStack(ModItems.WRIGHT.get(), count);
        writeParts(stack, parts);
        writeName(stack, name);
        return stack;
    }

    public static ItemStack stack(Swarm.Unit unit) {
        return stack(unit.parts(), unit.name(), 1);
    }

    /** One unnamed wright of every part combination, Basic first. Used to match crafting patterns. */
    public static List<ItemStack> variants() {
        if (variants == null) {
            List<ItemStack> all = new ArrayList<>();
            for (int rotor = WrightTier.BASIC; rotor <= WrightTier.APEX; rotor++) {
                for (int fabricator = WrightTier.BASIC; fabricator <= WrightTier.APEX; fabricator++) {
                    for (int core = WrightTier.BASIC; core <= WrightTier.APEX; core++) {
                        all.add(stack(new Loadout(rotor, fabricator, core), "", 1));
                    }
                }
            }
            variants = List.copyOf(all);
        }
        return variants.stream().map(ItemStack::copy).toList();
    }

    private static List<ItemStack> variants;

    public static Loadout parts(ItemStack stack) {
        Loadout parts = stack.get(ModDataComponents.WRIGHT_PARTS.get());
        if (parts != null) {
            return parts;
        }
        Integer retired = stack.get(ModDataComponents.RETIRED_WRIGHT_TIER.get());
        return retired == null ? Loadout.BASIC : Loadout.BASIC.with(PartSlot.ROTOR, retired);
    }

    public static void writeParts(ItemStack stack, Loadout parts) {
        stack.remove(ModDataComponents.RETIRED_WRIGHT_TIER.get());
        if (parts == null || parts.isBasic()) {
            stack.remove(ModDataComponents.WRIGHT_PARTS.get());
        } else {
            stack.set(ModDataComponents.WRIGHT_PARTS.get(), parts);
        }
    }

    public static String nameOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return Swarm.cleanName(stack.get(ModDataComponents.WRIGHT_NAME.get()));
    }

    public static void writeName(ItemStack stack, String name) {
        String clean = Swarm.cleanName(name);
        if (clean.isBlank()) {
            stack.remove(ModDataComponents.WRIGHT_NAME.get());
        } else {
            stack.set(ModDataComponents.WRIGHT_NAME.get(), clean);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && stack.has(ModDataComponents.RETIRED_WRIGHT_TIER.get())) {
            writeParts(stack, parts(stack));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Loadout parts = parts(stack);
        int color = WrightTier.textColor(parts.topTier()) & 0xFFFFFF;
        String called = nameOf(stack);
        if (!called.isBlank()) {
            return Component.translatable("screen.wrights.drone_named", called).withColor(color);
        }
        return Component.translatable("item.wrights.wright").withColor(color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Loadout parts = parts(stack);
        for (PartSlot slot : PartSlot.values()) {
            int tier = parts.tier(slot);
            Component fitted = Component.translatable(slot.fittedKey(tier)).withColor(WrightTier.textColor(tier) & 0xFFFFFF);
            tooltip.add(Component.translatable("item.wrights.wright.part", Component.translatable(slot.nameKey()), fitted)
                    .withStyle(ChatFormatting.GRAY));
            for (Component line : WrightPartItem.stats(slot, tier)) {
                tooltip.add(Component.literal("  ").append(line).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(energyLine(parts));
        tooltip.add(Component.translatable("item.wrights.wright.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** "Energy: 87% of the block cost", green below 100%, red above. */
    public static Component energyLine(Loadout parts) {
        int percent = parts.energyPercent();
        ChatFormatting color = percent < 100 ? ChatFormatting.GREEN : percent > 100 ? ChatFormatting.RED : ChatFormatting.GRAY;
        String key = parts.rotor() == parts.core() && parts.rotor() > WrightTier.BASIC
                ? "item.wrights.wright.energy_matched" : "item.wrights.wright.energy";
        return Component.translatable(key, Component.literal(percent + "%").withStyle(color)).withStyle(ChatFormatting.GRAY);
    }

    /** Right-click a part onto a wright: fits it, and the part it replaces goes to the cursor. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof WrightPartItem part) || !slot.allowModification(player)) {
            return false;
        }
        Loadout parts = parts(stack);
        int fitted = parts.tier(part.slot());
        if (fitted == part.tier()) {
            return true;
        }
        ItemStack target = stack.getCount() > 1 ? stack.split(1) : stack;
        writeParts(target, parts.with(part.slot(), part.tier()));
        other.shrink(1);
        Item old = part.slot().item(fitted);
        if (old != null) {
            ItemStack back = new ItemStack(old);
            if (other.isEmpty()) {
                access.set(back);
            } else {
                hand(player, back);
            }
        }
        if (target != stack) {
            hand(player, target);
        }
        slot.setChanged();
        player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 1.2F);
        return true;
    }

    /** Right-click a single wright onto an empty slot: takes one part out into that slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || stack.getCount() != 1 || slot.hasItem()) {
            return false;
        }
        Loadout parts = parts(stack);
        for (PartSlot partSlot : PartSlot.values()) {
            Item part = partSlot.item(parts.tier(partSlot));
            if (part == null) {
                continue;
            }
            ItemStack out = new ItemStack(part);
            if (!slot.mayPlace(out)) {
                return false;
            }
            slot.setByPlayer(out);
            writeParts(stack, parts.with(partSlot, WrightTier.BASIC));
            player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 1.2F);
            return true;
        }
        return false;
    }

    private static void hand(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty() && !player.level().isClientSide) {
            player.drop(stack, false);
        }
    }
}
