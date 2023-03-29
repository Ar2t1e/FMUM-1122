package com.mcwb.common.operation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class Operation< T > implements IOperation
{
	protected T equipped;
	protected IOperationController controller;
	
	protected float prevProgress;
	protected float progress;
	
	protected int ieffect;
	protected int isound;
	
	protected Operation( T equipped, IOperationController controller )
	{
		this.equipped = equipped;
		this.controller = controller;
	}
	
	@Override
	public float progress() { return this.progress; }
	
	@Override
	@SideOnly( Side.CLIENT )
	public float getProgress( float smoother ) {
		return this.prevProgress + ( this.progress - this.prevProgress ) * smoother;
	}
	
	@Override
	public IOperation tick( EntityPlayer player )
	{
		// Update progress
		this.prevProgress = this.progress;
		this.progress = Math.min( 1F, this.progress + this.controller.progressor() );
		
		// Handle effects
		for (
			final int effectCount = this.controller.effectCount();
			this.ieffect < effectCount
				&& this.controller.getEffectTime( this.ieffect ) <= this.progress;
			++this.ieffect
		) this.doHandleEffect( player );
		
		// Handle sounds
		for (
			final int soundCount = this.controller.soundCount();
			this.isound < soundCount
				&& this.controller.getSoundTime( this.isound ) <= this.progress;
			++this.isound
		) this.controller.handlePlaySound( this.isound, player );
		
		return this.prevProgress == 1F ? this.onComplete( player ) : this;
	}
	
	@Override
	public IOperation onOtherTryLaunch( IOperation op, EntityPlayer player ) { return this; }
	
	@Override
	public String toString()
	{
		final String typeName = this.getClass().getTypeName();
		final String className = typeName.substring( typeName.lastIndexOf( '.' ) );
		return "Operation::" + className + "<" + this.equipped + ">";
	}
	
	protected void clearProgress()
	{
		this.prevProgress = 0F;
		this.progress = 0F;
		this.ieffect = 0;
		this.isound = 0;
	}
	
	protected IOperation onComplete( EntityPlayer player ) { return this.terminate( player ); }
	
	/**
	 * Handle effect specified by {@link #ieffect}
	 */
	protected void doHandleEffect( EntityPlayer player ) { }
}
