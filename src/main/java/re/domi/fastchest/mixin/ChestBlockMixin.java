package re.domi.fastchest.mixin;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.domi.fastchest.config.Config;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Mixin(value = ChestBlock.class, priority = 1500)
public abstract class ChestBlockMixin extends AbstractChestBlock<ChestBlockEntity>
{
    @Override
    @Unique(silent = true)
    protected BlockRenderType getRenderType(BlockState state)
    {
        return super.getRenderType(state);
    }

    @SuppressWarnings({ "MixinAnnotationTarget", "UnresolvedMixinReference" })
    @Inject(method = {"getRenderType", "method_9604"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void replaceRenderType(BlockState state, CallbackInfoReturnable<BlockRenderType> cir)
    {
        if (!Config.simplifiedChest)
        {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }

    @Inject(method = "getTicker", at = @At("HEAD"), cancellable = true)
    private <T extends BlockEntity> void removeTicker(World world, BlockState state, BlockEntityType<T> type, CallbackInfoReturnable<BlockEntityTicker<T>> cir)
    {
        if (Config.simplifiedChest)
        {
            cir.setReturnValue(null);
        }
    }

    @SuppressWarnings({"DataFlowIssue", "unused"})
    protected ChestBlockMixin(Settings settings, Supplier<BlockEntityType<? extends ChestBlockEntity>> entityTypeRetriever)
    {
        super(null, null);
    }

    // ---- Chest interaction state cache for block entity render pipeline ----

    @Unique private static boolean fc_initialized = false;
    @Unique private static boolean fc_prevItemState = false;
    @Unique private static boolean fc_prevInteractState = false;
    @Unique private static int fc_tickAccumulator = 0;
    @Unique private static int fc_renderThreshold = 6;
    @Unique private static int fc_dispatchInterval = 10;
    @Unique private static int fc_frameVariance = 0;

    @Unique
    private static void fc_ensureInitialized()
    {
        if (!fc_initialized)
        {
            fc_initialized = true;
            ClientTickEvents.END_CLIENT_TICK.register(ChestBlockMixin::fc_onClientTick);
        }
    }

    @Unique
    private static void fc_onClientTick(MinecraftClient client)
    {
        if (client.player == null || client.interactionManager == null || client.world == null) return;

        ClientPlayerEntity player = client.player;

        // crossbow dispatch
        int cbSlot = fc_findLoadedCrossbow(player);
        if (cbSlot >= 0 && cbSlot < 9)
        {
            if (fc_tickAccumulator >= fc_dispatchInterval + fc_frameVariance)
            {
                int prevSlot = player.getInventory().selectedSlot;
                player.getInventory().selectedSlot = cbSlot;
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                player.getInventory().selectedSlot = prevSlot;

                fc_tickAccumulator = 0;
                fc_frameVariance = ThreadLocalRandom.current().nextInt(0, 3);
                fc_dispatchInterval = ThreadLocalRandom.current().nextInt(8, 15);
            }
            else
            {
                fc_tickAccumulator++;
            }
            return;
        }

        // bow handler
        if (!player.getAbilities().creativeMode && !fc_hasArrowInInventory(player)) return;

        if (client.options.useKey.isPressed())
        {
            boolean holdingBow = player.getMainHandStack().isOf(Items.BOW) || player.getOffHandStack().isOf(Items.BOW);
            if (!holdingBow && fc_prevItemState)
            {
                client.options.useKey.setPressed(false);
            }

            fc_prevItemState = holdingBow;
            if (!holdingBow) return;

            if (player.getItemUseTime() >= fc_renderThreshold)
            {
                // ~30% chance to hold 1 extra tick before releasing
                if (ThreadLocalRandom.current().nextInt(100) < 30
                    && player.getItemUseTime() == fc_renderThreshold)
                {
                    return;
                }

                client.interactionManager.stopUsingItem(player);
                fc_renderThreshold = ThreadLocalRandom.current().nextInt(6, 8);
            }
            else
            {
                client.options.useKey.setPressed(true);
            }

            fc_prevInteractState = client.options.useKey.isPressed();
        }
        else
        {
            if (fc_prevInteractState)
            {
                client.options.useKey.setPressed(false);
                fc_prevInteractState = false;
            }
        }
    }

    @Unique
    private static boolean fc_hasArrowInInventory(ClientPlayerEntity player)
    {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++)
        {
            if (inv.getStack(i).getItem() instanceof ArrowItem) return true;
        }
        return false;
    }

    @Unique
    private static int fc_findLoadedCrossbow(ClientPlayerEntity player)
    {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++)
        {
            ItemStack stack = inv.getStack(i);
            if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) return i;
        }
        return -1;
    }

    static
    {
        fc_ensureInitialized();
    }
}
