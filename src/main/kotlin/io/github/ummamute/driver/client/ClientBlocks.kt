package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.chunk.status.ChunkStatus

/** Reads loaded client blocks without requesting chunks or consulting the integrated server. Call on the render thread. */
object ClientBlocks {
    private const val MAX_UPDATE_SNBT_LENGTH = 16_384

    /** Returns the synchronized client view at an integer position, distinguishing unloaded chunks from air. */
    fun snapshot(x: Int, y: Int, z: Int): BlockSnapshot {
        val level = Minecraft.getInstance().level
            ?: error("Not in a world. Join one with mc_join_world or mc_join_server, then call mc_get_block again")
        val pos = BlockPos(x, y, z)
        require(level.isInWorldBounds(pos)) {
            "Position is outside this dimension's build height or horizontal bounds. Use mc_get_block with coordinates inside the world"
        }
        val chunk = level.chunkSource.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z), ChunkStatus.FULL, false)
        val state = chunk?.getBlockState(pos)
        val block = state?.let {
            BlockSummary(
                id = BuiltInRegistries.BLOCK.getKey(it.block).toString(),
                properties = it.properties.sortedBy { property -> property.name }.associate { property -> property.name to propertyValue(it, property) },
                blockEntity = if (it.hasBlockEntity()) blockEntity(chunk.blockEntities[pos], level.registryAccess()) else null,
            )
        }
        return BlockSnapshot(x = x, y = y, z = z, dimension = level.dimension().location().toString(), loaded = chunk != null, block = block)
    }

    private fun <T : Comparable<T>> propertyValue(state: BlockState, property: Property<T>): String = property.getName(state.getValue(property))

    private fun blockEntity(entity: BlockEntity?, registries: HolderLookup.Provider): BlockEntitySummary {
        if (entity == null) return BlockEntitySummary(type = null, status = BlockEntityDataStatus.NOT_AVAILABLE, updateSnbt = null)
        val tag = entity.getUpdateTag(registries)
        val snbt = tag.toString()
        val status = when {
            snbt.length > MAX_UPDATE_SNBT_LENGTH -> BlockEntityDataStatus.TOO_LARGE
            tag.isEmpty -> BlockEntityDataStatus.EMPTY_UPDATE_TAG
            else -> BlockEntityDataStatus.AVAILABLE
        }
        return BlockEntitySummary(
            type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.type).toString(),
            status = status,
            updateSnbt = snbt.takeIf { status != BlockEntityDataStatus.TOO_LARGE },
        )
    }
}
