package world.cutters.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PistonStructureResolver.class)
public class PistonStructureResolverMixin {

	@Shadow private Level level;
	@Shadow private Direction pushDirection;
	@Shadow private List<BlockPos> toPush;
	@Shadow private List<BlockPos> toDestroy;

	@Shadow private static boolean isSticky(BlockState state) { return false; }

	@Inject(
		method = "resolve",
		at = @At(
			value = "RETURN"
		)
	)
	private void afterResolve(CallbackInfoReturnable<Boolean> cir) {
		toPush.removeAll(toDestroy);
	}

	@Inject(
		method = "addBlockLine",
		locals = LocalCapture.CAPTURE_FAILHARD,
		at = @At(
			value = "INVOKE",
			shift = Shift.BEFORE,
			ordinal = 0,
			target = "Ljava/util/List;size()I"
		)
	)
	private void undoBreakBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir, BlockState state) {
		if (state.is(Blocks.STONECUTTER)) {
			BlockPos behindPos = pos.relative(pushDirection.getOpposite());
			BlockState behindState = level.getBlockState(behindPos);

			if (canBeBroken(behindState) && toPush.contains(behindPos)) {
				toDestroy.remove(behindPos);
			}
		} else
		if (canBeBroken(state)) {
			BlockPos frontPos = pos.relative(pushDirection);
			BlockState frontState = level.getBlockState(frontPos);

			if (!frontState.is(Blocks.STONECUTTER) || toPush.contains(frontPos)) {
				toDestroy.remove(pos);
			}
		}
	}

	@Inject(
		method = "addBlockLine",
		cancellable = true,
		locals = LocalCapture.CAPTURE_FAILHARD,
		slice = @Slice(
			from = @At(
				value = "INVOKE",
				shift = Shift.BEFORE,
				ordinal = 0,
				target = "Lnet/minecraft/world/level/block/state/BlockState;getPistonPushReaction()Lnet/minecraft/world/level/material/PushReaction;"
			)
		),
		at = @At(
			value = "INVOKE",
			shift = Shift.BEFORE,
			ordinal = 0,
			target = "Ljava/util/List;size()I"
		)
	)
	private void tryBreakBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir, BlockState movedState, int i, int j, int k, BlockPos movedPos) {
		if (movedState.is(Blocks.STONECUTTER)) {
			BlockPos behindPos = movedPos.relative(pushDirection.getOpposite());
			BlockState behindState = level.getBlockState(behindPos);

			if (canBeBroken(behindState)) {
				toDestroy.add(behindPos);
				cir.setReturnValue(true);
			}
		} else
		if (canBeBroken(movedState)) {
			BlockPos behindPos = movedPos.relative(pushDirection.getOpposite());
			BlockState behindState = level.getBlockState(behindPos);

			if (behindState.is(Blocks.STONECUTTER)) {
				toDestroy.add(movedPos);
				cir.setReturnValue(true);
			}
		}
	}

	private boolean canBeBroken(BlockState state) {
		return !state.is(Blocks.STONECUTTER)
			&& !(state.getBlock() instanceof AbstractGlassBlock)
			&& !state.is(Blocks.GLASS_PANE)
			&& !(state.getBlock() instanceof StainedGlassPaneBlock)
			&& !isSticky(state);
	}
}
