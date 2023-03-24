package com.mcwb.client.camera;

import com.mcwb.client.IAutowireSmoother;
import com.mcwb.client.MCWBClient;
import com.mcwb.client.input.InputHandler;
import com.mcwb.client.player.PlayerPatchClient;
import com.mcwb.common.ModConfig;
import com.mcwb.util.DynamicPos;
import com.mcwb.util.Vec3f;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * This implementation includes the critical code that makes free view work. Hence it is recommended
 * to implement your camera controller from this class. This also provides some simple camera
 * effects that can be tuned via {@link ModConfig}.
 * FIXME: 左右归中停住无抖动 上下有
 * @author Giant_Salted_Fish
 */
@SideOnly( Side.CLIENT )
public class CameraAnimator implements ICameraController, IAutowireSmoother
{
	public static final CameraAnimator INSTANCE = new CameraAnimator();
	
	protected static final float PITCH_SHIFTER = Float
		.intBitsToFloat( ( Float.floatToIntBits( 90F ) >>> 23 ) - 23 << 23 );
	
	/**
	 * This is static and shared for all its sub-type instances to make sure the cycle will not jump
	 * when switching the camera animator
	 */
	protected static float dropDistanceCycle = 0F;
	
	/**
	 * The actual camera rotation in world coordinate(player rot + off-axis)
	 * 
	 * TODO: also save camera easing in it so that we dont need to calculate on {@link #getCameraRot(Vec3f)} every time
	 */
	protected final Vec3f cameraRot = new Vec3f();
	
	/**
	 * Direction that the player is heading
	 */
	protected final Vec3f playerRot = new Vec3f();
	
	/**
	 * Off-axis view angle that triggered by free view
	 */
	protected final DynamicPos cameraOffAxis = new DynamicPos();
	protected final Vec3f prevCameraOffAxis = new Vec3f();
	
	/**
	 * Handles easing animation on camera. For example the drop camera shake.
	 */
	protected final DynamicPos cameraEasing = new DynamicPos();
	
	/**
	 * Handles the camera effects
	 */
	@Override
	public void tick()
	{
		// If looking around, clear camera recover speed
		if( InputHandler.FREE_VIEW.down || InputHandler.CO_FREE_VIEW.down )
			this.cameraOffAxis.velocity.setZero();
		
		// Otherwise, update off-axis rotation
		else this.cameraOffAxis.update( 0.125F, 50F, 0.4F );
		
		/// Apply drop camera effects ///
		final PlayerPatchClient patch = PlayerPatchClient.instance;
		
		final float dropSpeed = Math.min( 0F, patch.playerVelocity.y );
		dropDistanceCycle += dropSpeed * MCWBClient.camDropCycle;
		
		this.cameraEasing.velocity.z += dropSpeed * MCWBClient.camDropAmpl
			* MathHelper.sin( dropDistanceCycle );
		
		// Apply drop impact on camera if upon hitting the ground
		if( patch.prevPlayerVelocity.y < 0F && patch.playerAcceleration.y > 0F )
		{
			final Vec3f headVelocity = this.cameraEasing.velocity;

//			boolean positive = MCWB.rand.nextBoolean();
			// Make sure the drop impact always makes the head tilt harder on its original direction
			boolean positive = headVelocity.z > 0F;
			if( positive ^ this.cameraEasing.curPos.z > 0F )
			{
				headVelocity.z = -headVelocity.z;
				positive = !positive;
			}
			
			// Value of cap#playerAcceleration#y has to be positive here
			final float impact = patch.playerAcceleration.y * MCWBClient.camDropImpact;
			headVelocity.z += positive ? impact : -impact;
		}
		
		this.cameraEasing.update( 1F, 4.25F, 0.4F );
	}
	
	/**
	 * Handles the view update upon mouse input
	 */
	@Override
	public void prepareRender( MouseHelper mouse )
	{
		final EntityPlayerSP player = MCWBClient.MC.player;
		final DynamicPos camOffAxis = this.cameraOffAxis;
		final Vec3f playerRot = this.playerRot;
		final float smoother = this.smoother();
		
		// Process input mouse delta
		final float mouseFactor = this.getMouseFactor();
		final float mouseDeltaY = MCWBClient.SETTINGS.invertMouse ? mouse.deltaY : -mouse.deltaY;
		final float rawDeltaPitch = mouseDeltaY * mouseFactor;
		final float rawDeltaYaw = mouse.deltaX * mouseFactor;
		
		// Make sure delta pitch is inside the limit
		final float camOffAxisX = camOffAxis.getX( smoother );
		final float rawCameraPitch = player.rotationPitch + camOffAxisX;
		final float newCameraPitch = MathHelper.clamp( rawCameraPitch + rawDeltaPitch, -90F, 90F );
		final float deltaPitch = newCameraPitch - rawCameraPitch;
		
		// If looking around, apply view rot to off-axis
		if( InputHandler.FREE_VIEW.down || InputHandler.CO_FREE_VIEW.down )
		{
			playerRot.set( player.rotationPitch, player.rotationYaw, 0F );
			
			final Vec3f offAxis = camOffAxis.curPos;
			// This is commented as the state of look around key is updated by tick so there is no \
			// way it can change in between the ticks. And may be the key update event is earlier \
			// than the item tick hence set it with smoothed value will actually cause that view \
			// jump effect.
//			camOffAxis.smoothedPos( offAxis, smoother );
			offAxis.x += deltaPitch;
			
			// Make the yaw rotation would not exceed the limit
			final float pitchSquared = newCameraPitch * newCameraPitch;
			final float yawLimitSquared = MCWBClient.freeViewLimitSquared - pitchSquared;
			final float yawLimit = MathHelper.sqrt( yawLimitSquared );
			offAxis.y = MathHelper.clamp( offAxis.y + rawDeltaYaw, -yawLimit, yawLimit );
			
			// Set previous with current value to avoid bobbing
			camOffAxis.prevPos.set( offAxis );
			
			// Clear mouse input to prevent walking direction change
			mouse.deltaX = 0;
			mouse.deltaY = 0;
			
			this.updateCameraRot( offAxis.x, offAxis.y );
		}
		else
		{
			playerRot.set(
				player.rotationPitch + deltaPitch,
				player.rotationYaw + rawDeltaYaw,
				0F
			);
			this.updateCameraRot( camOffAxisX, camOffAxis.getY( smoother ) );
		}
	}
	
	protected final void updateCameraRot( float camOffAxisX, float camOffAxisY )
	{
		this.cameraRot.x = this.playerRot.x + camOffAxisX;
		this.cameraRot.y = this.playerRot.y + camOffAxisY;
		
		// Apply a tiny change to player's view to force view frustum update if off-axis has changed
		final EntityPlayerSP player = MCWBClient.MC.player;
		final Vec3f prevOffAxis = this.prevCameraOffAxis;
		final float pitchChange = Math.abs( camOffAxisX - prevOffAxis.x );
		final float yawChange   = Math.abs( camOffAxisY - prevOffAxis.y );
		
		final float rawShifter = Math.min( PITCH_SHIFTER, pitchChange + yawChange );
		final float clampedShifter = rawShifter < PITCH_SHIFTER ? 0F : rawShifter;
		player.rotationPitch += clampedShifter;
		player.prevRotationPitch = player.rotationPitch;
		
		prevOffAxis.x = camOffAxisX;
		prevOffAxis.y = camOffAxisY;
	}
	
	@Override
	public void getCameraRot( Vec3f dst )
	{
		this.cameraEasing.get( dst, this.smoother() );
		dst.add( this.cameraRot );
	}
	
	@Override
	public void getPlayerRot( Vec3f dst ) { dst.set( this.playerRot ); }
	
	protected final float getMouseFactor()
	{
		final float factor = MCWBClient.SETTINGS.mouseSensitivity * 0.6F + 0.2F;
		return factor * factor * factor * 8F * 0.15F;
	}
}
