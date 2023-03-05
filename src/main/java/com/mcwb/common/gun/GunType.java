package com.mcwb.common.gun;

import java.util.function.BiConsumer;

import com.mcwb.client.gun.IGunPartRenderer;
import com.mcwb.client.input.InputHandler;
import com.mcwb.client.player.PlayerPatchClient;
import com.mcwb.client.render.IAnimator;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.meta.IMeta;
import com.mcwb.common.module.IModular;
import com.mcwb.common.module.IModuleEventSubscriber;
import com.mcwb.util.ArmTracker;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class GunType< C extends IGun< ? >, R extends IGunPartRenderer< ? super C > >
	extends GunPartType< C, R >
{
	public static final BuildableLoader< IMeta >
		LOADER = new BuildableLoader<>( "gun", GunJson.class );
	
	@Override
	protected IMeta loader() { return LOADER; }
	
	protected class Gun< T extends IGunPart< ? extends T > >
		extends GunPart< T > implements IGun< T >
	{
		protected transient IGunPart< ? > leftHandHolding = this;
		protected transient IGunPart< ? > rightHandHolding = this;
		
		protected Gun() { }
		
		protected Gun( NBTTagCompound nbt ) { super( nbt ); }
		
		@Override
		public void updateState( BiConsumer< Class< ? >, IModuleEventSubscriber< ? > > registry )
		{
			super.updateState( registry );
			
			this.leftHandHolding = this; // TODO: Validate if necessary
			this.rightHandHolding = this;
			this.forEach( gunPart -> {
				if( gunPart.leftHandPriority() > this.leftHandHolding.leftHandPriority() )
					this.leftHandHolding = gunPart;
				if( gunPart.rightHandPriority() > this.rightHandHolding.rightHandPriority() )
					this.rightHandHolding = gunPart;
			} );
		}
		
		@Override
		@SideOnly( Side.CLIENT )
		public boolean updateViewBobbing( boolean original ) { return false; }
		
		@Override
		@SideOnly( Side.CLIENT )
		public boolean hideCrosshair()
		{
			final boolean modifying = PlayerPatchClient.instance.executing() == this.opModify();
			final boolean freeView = InputHandler.FREE_VIEW.down || InputHandler.CO_FREE_VIEW.down;
			return !( modifying && freeView );
		}
		
		@Override
		@SideOnly( Side.CLIENT )
		public void setupRenderArm( ArmTracker leftArm, ArmTracker rightArm, IAnimator animator )
		{
			this.leftHandHolding.setupLeftArmToRender( leftArm, animator );
			this.rightHandHolding.setupRightArmToRender( rightArm, animator );
		}
	}
	
	protected static class GunWrapper<
		M extends IGunPart< ? extends M >,
		T extends IGun< ? extends M >
	> extends GunPartWrapper< M, T > implements IGun< M >
	{
		protected GunWrapper( T primary, ItemStack stack ) { super( primary, stack ); }
		
		@Override
		@SideOnly( Side.CLIENT )
		public void setupRenderArm( ArmTracker leftArm, ArmTracker rightArm, IAnimator animator ) {
			throw new RuntimeException();
		}
	}
	
	private static class GunJson extends GunType< IGun< ? >, IGunPartRenderer< ? super IGun< ? > > >
	{
		@Override
		public IModular< ? > newRawContexted()
		{
			return this.new Gun< IGunPart< ? > >()
			{
				// Override this so that we do not need to create a wrapper for it
				@Override
				public void syncAndUpdate() { }
			};
		}
		
		@Override
		public IModular< ? > deserializeContexted( NBTTagCompound nbt ) {
			return this.new Gun<>( nbt );
		}
		
		@Override
		protected ICapabilityProvider newWrapper( IGun< ? > primary, ItemStack stack ) {
			return new GunWrapper<>( primary, stack );
		}
	}
}
