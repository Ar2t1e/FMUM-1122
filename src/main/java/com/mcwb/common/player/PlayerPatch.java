package com.mcwb.common.player;

import javax.annotation.Nullable;

import com.mcwb.common.item.IContextedItem;
import com.mcwb.common.item.IItemMeta;
import com.mcwb.common.item.IItemMetaHost;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

public class PlayerPatch implements ICapabilityProvider
{
	@CapabilityInject( PlayerPatch.class )
	public static final Capability< PlayerPatch > CAPABILITY = null;
	
	/**
	 * Host player of this patch
	 */
	protected final EntityPlayer player;
	
	/**
	 * Current operation that is executing
	 */
	protected IOperation operating = IOperation.NONE;
	
	/// Stuffs for main hand item ///
	protected ItemStack prevMainStack = ItemStack.EMPTY;
	protected IContextedItem prevMainItem = IContextedItem.EMPTY;
	protected int prevSlotIdx = -1; // TODO: better initialize?
	
	/// Stuffs for off-hand item ///
	protected IItemMeta prevOffMeta = null; // null is on purpose
	protected IContextedItem prevOffItem = IContextedItem.EMPTY;
	
	public PlayerPatch( EntityPlayer player ) { this.player = player; }
	
	public void tick()
	{
		final InventoryPlayer inv = this.player.inventory;
		
		/// Off-hand stuff ///
		{
			final ItemStack stack = inv.offHandInventory.get( 0 );
			final IItemMeta meta = IItemMetaHost.getMeta( stack );
			if( meta != this.prevOffMeta )
			{
				final IContextedItem item = meta.getContexted( stack );
				this.prevOffItem.onPutAway( item, this.player, EnumHand.OFF_HAND );
				item.onTakeOut( this.prevOffItem, this.player, EnumHand.OFF_HAND );
				
				// Do not forget to update last tick variables
				this.prevOffItem = item;
				this.prevOffMeta = meta;
			}
//			else if( offStack != this.prevOffStack ) // TODO: maybe call stack change for off-hand item
		}
		
		/// Main hand stuff ///
		{
			final ItemStack stack = inv.getCurrentItem();
			final IItemMeta meta = IItemMetaHost.getMeta( stack );
			
			// Check selected slot switch
			if( inv.currentItem != this.prevSlotIdx )
			{
				// Fire events for holding item change
				final IContextedItem item = meta.getContexted( stack );
				this.prevMainItem.onPutAway( item, this.player, EnumHand.MAIN_HAND );
				this.operating = this.operating
					.onInvSlotChange( item, inv.currentItem, this.prevSlotIdx );
				item.onTakeOut( this.prevMainItem, this.player, EnumHand.MAIN_HAND );
				
				// Do not forget to update last tick variables
				this.prevMainItem = item;
				this.prevMainStack = stack;
				this.prevSlotIdx = inv.currentItem;
			}
			else if( stack != this.prevMainStack )
			{
				// Fire operating callback for stack change
				final IContextedItem item = meta.getContexted( stack );
				this.operating = this.operating.onHoldingStackChange( item );
				
				// Update last tick variables
				this.prevMainItem = item;
				this.prevMainStack = stack;
			}
		}
		
		// Tick current operating
		this.operating = this.operating.tick();
	}
	
	public IOperation operating() { return this.operating; }
	
	public IOperation tryLaunch( IOperation op ) { return this.operating.onOtherTryLaunch( op ); }
	
	public IOperation ternimateOperating() { return this.operating = this.operating.terminate(); }
	
	public IOperation toggleOperating() { return this.operating = this.operating.toggle(); }
	
	@Override
	public boolean hasCapability( Capability< ? > capability, @Nullable EnumFacing facing ) {
		return capability == CAPABILITY;
	}
	
	@Override
	public < T > T getCapability( Capability< T > capability, @Nullable EnumFacing facing ) {
		return CAPABILITY.cast( this );
	}
	
	public static PlayerPatch get( EntityPlayer player ) {
		return player.getCapability( CAPABILITY, null );
	}
}
