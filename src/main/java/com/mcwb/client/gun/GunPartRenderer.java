package com.mcwb.client.gun;

import com.mcwb.client.item.ModifiableItemRenderer;
import com.mcwb.client.render.IAnimator;
import com.mcwb.client.render.IRenderer;
import com.mcwb.common.MCWB;
import com.mcwb.common.gun.IGunPart;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.util.ArmTracker;

import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class GunPartRenderer< T extends IGunPart > extends ModifiableItemRenderer< T >
	implements IGunPartRenderer< T >
{
	public static final BuildableLoader< IRenderer >
		LOADER = new BuildableLoader< IRenderer >(
			"gun_part", json -> MCWB.GSON.fromJson( json, GunPartRenderer.class )
		); // TODO: kind of weird as passing class works with ide but fails the compile
	
	@Override
	public void setupLeftArmToRender( ArmTracker leftArm, IAnimator animator )
	{
		leftArm.handPos.set( 0F );
		leftArm.armRotZ = 0F;
		leftArm.$handRotZ( 0F );
		this.updateArm( leftArm, animator );
	}
	
	@Override
	public void setupRightArmToRender( ArmTracker rightArm, IAnimator animator )
	{
		rightArm.handPos.set( 0F );
		rightArm.armRotZ = 0F;
		rightArm.$handRotZ( 0F );
		this.updateArm( rightArm, animator );
	}
	
	@Override
	protected GunPartAnimatorState animator( EnumHand hand ) {
		return GunAnimatorState.INSTANCE;
	}
	
	protected void updateArm( ArmTracker arm, IAnimator animator )
	{
		final GunPartAnimatorState state = GunAnimatorState.INSTANCE;
		animator.getChannel( GunPartAnimatorState.CHANNEL_ITEM, this.smoother(), state.m0 );
		animator.applyChannel( GunPartAnimatorState.CHANNEL_INSTALL, this.smoother(), state.m0 );
		
		state.m0.apply( arm.handPos );
		arm.updateArmOrientation();
	}
}
