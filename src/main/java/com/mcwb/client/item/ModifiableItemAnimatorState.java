package com.mcwb.client.item;

import com.mcwb.util.Vec3f;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class ModifiableItemAnimatorState extends ItemAnimatorState
{
//	public OpModifyClient modifyOp = ModifiableItemType.OP_MODIFY;
	
	public Vec3f modifyPos = Vec3f.ORIGIN;
	
//	@Override
//	public void applyChannel( String channel, float smoother, Mat4f dst )
//	{
//		final Vec3f vec = this.v0;
//		switch( channel )
//		{
//		case IItemRenderer.CHANNEL_ITEM:
//			final float modifyProgress = this.modifyOp.getProgress( smoother );
//			
//			// Translation
//			this.holdPos.get( vec, smoother );
//			this.v0.scale( 1F - modifyProgress );
//			dst.translate( vec );
//			
//			vec.set( this.modifyPos );
//			vec.scale( modifyProgress );
//			dst.translate( vec );
//			
//			// Rotation
//			this.holdRot.get( vec, smoother );
//			vec.scale( 1F - modifyProgress );
//			
//			// TODO: player#rotate seems to have lag when being used to render 
//			final EntityPlayerSP player = MCWBClient.MC.player;
//			final float refPlayerYaw = this.modifyOp.refPlayerRotYaw;
//			final float modifyYawBase = ( refPlayerYaw % 360F + 360F ) % 360F - 180F; // TODO: maybe do this when capture ref player yaw
//			final float modifyYawDelta = refPlayerYaw - player.rotationYaw; // TODO: get this from camera controller
//			final float modifyYaw = modifyYawBase - modifyYawDelta;
//			
//			dst.rotateX( -player.rotationPitch * modifyProgress );
//			dst.eulerRotateYXZ( vec.x, vec.y + modifyYaw * modifyProgress, vec.z );
//			
//			break;
//			
//		default: //super.applyChannel( channel, smoother, dst );
//		}
//	}
}
