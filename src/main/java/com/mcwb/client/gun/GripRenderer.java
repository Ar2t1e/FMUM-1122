package com.mcwb.client.gun;

import com.mcwb.client.render.IAnimator;
import com.mcwb.client.render.IRenderer;
import com.mcwb.common.MCWB;
import com.mcwb.common.gun.IGunPart;
import com.mcwb.common.load.BuildableLoader;
import com.mcwb.common.load.IContentProvider;
import com.mcwb.util.ArmTracker;
import com.mcwb.util.Mat4f;
import com.mcwb.util.Vec3f;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class GripRenderer< T extends IGunPart< ? > > extends GunPartRenderer< T >
{
	public static final BuildableLoader< IRenderer > LOADER
		= new BuildableLoader<>( "grip", json -> MCWB.GSON.fromJson( json, GripRenderer.class ) );
	
	protected Vec3f handPos = Vec3f.ORIGIN;
	protected float handRotZ = 0F;
	protected float armRotZ = 0F;
	
	@Override
	public IRenderer build( String path, IContentProvider provider )
	{
		super.build( path, provider );
		
		this.handPos.scale( this.scale );
		return this;
	}
	
	@Override
	public void setupLeftArmToRender( ArmTracker leftArm, IAnimator animator ) {
		this.doSetupArmToRender( leftArm, animator );
	}
	
	@Override
	public void setupRightArmToRender( ArmTracker rightArm, IAnimator animator )
	{
		this.doSetupArmToRender( rightArm, animator );
//		
//		final GunPartAnimatorState state = GunAnimatorState.INSTANCE;
//		state.applyChannel( GunPartAnimatorState.CHANNEL_ITEM, this.smoother(), state.m0 );
//		final float gunRotZ = state.m0.getEulerAngleZ();
//		
//		rightArm.handPos.set( DevHelper.get( 1 ).getPos() );
//		rightArm.armRotZ = DevHelper.get( 1 ).getRot().x;
//		rightArm.$handRotZ( gunRotZ + DevHelper.get( 1 ).getRot().z );
//		
//		this.updateArm( rightArm, animator );
	}
	
	protected void doSetupArmToRender( ArmTracker arm, IAnimator animator )
	{
		final Mat4f mat = Mat4f.locate();
		IAnimator.getChannel( animator, CHANNEL_ITEM, mat ); // TODO: change to module, and default hand rot
		final float gunRotZ = mat.getEulerAngleZ();
		mat.release();
		
//		arm.handPos.set( DevHelper.get( 0 ).getPos() );
//		arm.$handRotZ( gunRotZ + DevHelper.get( 0 ).getRot().z );
//		arm.armRotZ = DevHelper.get( 0 ).getRot().x;
		
		arm.handPos.set( this.handPos );
		arm.$handRotZ( gunRotZ + this.handRotZ );
		arm.armRotZ = this.armRotZ;
		
		this.updateArm( arm, animator );
	}
}
