package com.fmum.common.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Represents {@link IItemType} with context.
 * 
 * @author Giant_Salted_Fish
 */
public interface IItem
{
	IItem VANILLA = new IItem()
	{
		@Override
		public int stackId() { return 0; }
		
		@Override
		public IEquippedItem< ? > onTakeOut( EntityPlayer player, EnumHand hand ) {
			return IEquippedItem.VANILLA;
		}
		
		@Override
		public IEquippedItem< ? > onStackUpdate(
			IEquippedItem< ? > prevEquipped,
			EntityPlayer player,
			EnumHand hand
		) { return IEquippedItem.VANILLA; }
		
		@Override
		@SideOnly( Side.CLIENT )
		public ResourceLocation texture() { return null; }
	};
	
	/**
	 * This will be used to determinate whether the item in hand has changed.
	 * 
	 * @return A universe id that identifies an item stack.
	 */
	int stackId();
	
	/**
	 * Called when player is trying to take out this item.
	 */
	IEquippedItem< ? > onTakeOut( EntityPlayer player, EnumHand hand );
	
	/**
	 * Called when the corresponding stack in hand has changed.
	 */
	IEquippedItem< ? > onStackUpdate(
		IEquippedItem< ? > prevEquipped,
		EntityPlayer player,
		EnumHand hand
	);
	
	@SideOnly( Side.CLIENT )
	ResourceLocation texture();
}
