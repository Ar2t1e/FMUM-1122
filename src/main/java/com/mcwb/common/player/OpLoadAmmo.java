package com.mcwb.common.player;

import com.mcwb.common.ammo.IAmmoType;
import com.mcwb.common.gun.IMag;
import com.mcwb.common.item.IItemType;
import com.mcwb.common.item.IItemTypeHost;
import com.mcwb.common.operation.IOperation;
import com.mcwb.common.operation.IOperationController;
import com.mcwb.common.operation.Operation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class OpLoadAmmo extends Operation
{
	protected final IMag< ? > mag;
	
	protected final int invSlot;
	
	protected IOperation next = NONE;
	
	public OpLoadAmmo( IMag< ? > mag, int invSlot, IOperationController controller )
	{
		super( controller );
		
		this.mag = mag;
		this.invSlot = invSlot;
	}
	
	@Override
	public IOperation launch( EntityPlayer player )
	{
		switch ( 0 )
		{
		default:
			if ( this.mag.isFull() ) { break; }
			
			final ItemStack stack = player.inventory.getStackInSlot( this.invSlot );
			final IItemType type = IItemTypeHost.getTypeOrDefault( stack.getItem() );
			final boolean isAmmo = type instanceof IAmmoType;
			final boolean isValidAmmo = isAmmo && this.mag.isAllowed( ( IAmmoType ) type );
			if ( !isValidAmmo ) { break; }
			
			return this;
		}
		return NONE;
	}
	
	@Override
	public IOperation onOtherTryLaunch( IOperation op, EntityPlayer player )
	{
		this.next = op;
		return this;
	}
	
	@Override
	protected IOperation onComplete( EntityPlayer player ) { return this.next.launch( player ); }
	
	@Override
	protected void doHandleEffect( EntityPlayer player )
	{
		final ItemStack stack = player.inventory.getStackInSlot( this.invSlot );
		final IItemType type = IItemTypeHost.getTypeOrDefault( stack.getItem() );
		final boolean isAmmo = type instanceof IAmmoType;
		if ( !isAmmo ) { return; }
		
		final IAmmoType ammo = ( IAmmoType ) type;
		if ( !this.mag.isAllowed( ammo ) ) { return; }
		
		this.mag.pushAmmo( ammo );
//		if ( !this.player.isCreative() )
			stack.shrink( 1 );
	}
}
