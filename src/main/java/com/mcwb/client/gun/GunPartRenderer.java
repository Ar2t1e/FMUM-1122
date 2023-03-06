package com.mcwb.client.gun;

import com.mcwb.client.item.ModifiableItemRenderer;
import com.mcwb.client.render.IAnimator;
import com.mcwb.client.render.IRenderer;
import com.mcwb.common.MCWB;
import com.mcwb.common.gun.IGunPart;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.util.ArmTracker;
import com.mcwb.util.Mat4f;

import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class GunPartRenderer< T extends IGunPart< ? > >
	extends ModifiableItemRenderer< T > implements IGunPartRenderer< T >
{
	public static final BuildableLoader< IRenderer >
		LOADER = new BuildableLoader<>(
			"gun_part",
			json -> MCWB.GSON.fromJson( json, GunPartRenderer.class )
		);
	
	@Override
	public void setupLeftArmToRender( ArmTracker leftArm, IAnimator animator )
	{
		leftArm.handPos.setZero();
		leftArm.armRotZ = 0F;
		leftArm.$handRotZ( 0F );
		this.updateArm( leftArm, animator );
	}
	
	@Override
	public void setupRightArmToRender( ArmTracker rightArm, IAnimator animator )
	{
		rightArm.handPos.setZero();
		rightArm.armRotZ = 0F;
		rightArm.$handRotZ( 0F );
		this.updateArm( rightArm, animator );
	}
	
	protected void updateArm( ArmTracker arm, IAnimator animator )
	{
		final Mat4f mat = Mat4f.locate();
		animator.getChannel( CHANNEL_ITEM, this.smoother(), mat );
		animator.applyChannel( CHANNEL_INSTALL, this.smoother(), mat );
		mat.transformAsPoint( arm.handPos );
		mat.release();
		
		arm.updateArmOrientation();
	}
	
	@Override
	protected GunPartAnimatorState animator( EnumHand hand ) {
		return GunAnimatorState.INSTANCE;
	}
}
