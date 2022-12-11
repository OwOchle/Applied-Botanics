package appbot.botania;

import java.util.ArrayList;
import java.util.List;

import com.google.common.primitives.Ints;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import vazkii.botania.api.corporea.CorporeaNode;
import vazkii.botania.api.corporea.CorporeaRequest;
import vazkii.botania.api.corporea.CorporeaSpark;
import vazkii.botania.common.impl.corporea.AbstractCorporeaNode;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.MEStorage;

public class MECorporeaNode extends AbstractCorporeaNode {

    private final MEStorage storage;
    private final IActionSource source;

    public MECorporeaNode(Level level, BlockPos pos, CorporeaSpark spark, MEStorage storage, IActionSource source) {
        super(level, pos, spark);
        this.storage = storage;
        this.source = source;
    }

    @Nullable
    public static CorporeaNode getNode(Level level, CorporeaSpark spark) {
        var accessor = IStorageMonitorableAccessor.SIDED.find(level, spark.getAttachPos(), Direction.UP);

        if (accessor != null) {
            var source = IActionSource.empty();
            var storage = accessor.getInventory(source);

            if (storage != null) {
                return new MECorporeaNode(level, spark.getAttachPos(), spark, storage, source);
            }
        }

        return null;
    }

    @Override
    public List<ItemStack> countItems(CorporeaRequest request) {
        return work(request, false);
    }

    @Override
    public List<ItemStack> extractItems(CorporeaRequest request) {
        return work(request, true);
    }

    protected List<ItemStack> work(CorporeaRequest request, boolean execute) {
        var list = new ArrayList<ItemStack>();

        for (var entry : storage.getAvailableStacks()) {
            var amount = Ints.saturatedCast(entry.getLongValue());

            if (entry.getKey()instanceof AEItemKey itemKey) {
                var stack = itemKey.toStack();

                if (request.getMatcher().test(stack)) {
                    request.trackFound(amount);
                    var remainder = Math.min(amount,
                            request.getStillNeeded() == -1 ? amount : request.getStillNeeded());

                    if (remainder > 0) {
                        request.trackSatisfied(remainder);

                        if (execute) {
                            if (!getSpark().isCreative()) {
                                remainder = (int) storage.extract(entry.getKey(), remainder, Actionable.MODULATE,
                                        source);
                            }

                            getSpark().onItemExtracted(stack);
                            request.trackExtracted(remainder);
                        }

                        while (remainder > 0) {
                            var taken = Math.min(remainder, stack.getMaxStackSize());
                            remainder -= taken;
                            list.add(itemKey.toStack(taken));
                        }
                    }
                }
            }
        }

        return list;
    }
}
