package com.mcwb.client.player;

import org.lwjgl.opengl.GL11;

import com.mcwb.client.EventHandlerClient;
import com.mcwb.client.MCWBClient;
import com.mcwb.client.camera.CameraAnimator;
import com.mcwb.client.camera.ICameraController;
import com.mcwb.client.input.IKeyBind;
import com.mcwb.common.item.IContextedItem;
import com.mcwb.common.player.PlayerPatch;
import com.mcwb.util.Vec3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.MouseHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public final class PlayerPatchClient extends PlayerPatch
{
	@CapabilityInject( PlayerPatchClient.class )
	public static final Capability< PlayerPatchClient > CAPABILITY = null;
	
	/**
	 * As there could only be one client player hence only one client patch instance here
	 */
	public static PlayerPatchClient instance;
	
	/**
	 * Player's eye position
	 */
	public final Vec3f
		playerPos = new Vec3f(),
		prevPlayerPos = new Vec3f();
	
	/**
	 * Player's raw velocity(referenced to player's eyes)
	 */
	public final Vec3f
		playerVelocity = new Vec3f(),
		prevPlayerVelocity = new Vec3f();
	
	/**
	 * Player's raw acceleration(referenced to player's eyes)
	 */
	public final Vec3f
		playerAcceleration = new Vec3f(),
		prevPlayerAcceleration = new Vec3f();
	
	// TODO: this part is a bit ugly
	public final Vec3f camRot = new Vec3f();
	
	// TODO: maybe wrap this
	public ICameraController cameraController = CameraAnimator.INSTANCE;
	
	/**
	 * <p> Hook mouse change method to do pre-render work. </p>
	 * 
	 * <p> Notice that this is kind of hack so you may need to check before you use it in other
	 * version of {@link Minecraft}. </p>
	 */
	private final MouseHelper mouseHelper = new MouseHelper()
	{
		@Override
		public void mouseXYChange()
		{
			super.mouseXYChange();
			
			// Call render tick for camera controller and item
			final PlayerPatchClient patch = PlayerPatchClient.this;
			patch.cameraController.onRenderTick( this );
			patch.prevMainItem.onRenderTick( EnumHand.MAIN_HAND );
			patch.prevOffItem.onRenderTick( EnumHand.OFF_HAND );
		}
	};
	
	public PlayerPatchClient( EntityPlayer player )
	{
		super( player );
		
		instance = this;
	}
	
	@Override
	public void tick()
	{
		super.tick();
		
		// Only update game settings when there is no GUI activated
		if( MCWBClient.MC.currentScreen == null )
		{
			final GameSettings settings = MCWBClient.SETTINGS;
			settings.viewBobbing = this.prevMainItem
				.updateViewBobbing( EventHandlerClient.oriViewBobbing );
		}
		
		// Update player velocity and acceleration
		this.prevPlayerPos.set( this.playerPos );
		this.playerPos.set(
			( float ) this.player.posX,
			( float ) this.player.posY + this.player.getEyeHeight(),
			( float ) this.player.posZ
		);
		
		this.prevPlayerVelocity.set( this.playerVelocity );
		this.playerVelocity.set( this.playerPos ).subtract( this.prevPlayerPos );
		
		this.prevPlayerAcceleration.set( this.playerAcceleration );
		this.playerAcceleration.set( this.playerVelocity ).subtract( this.prevPlayerVelocity );
		
		// Update camera effects
		this.cameraController.onLogicTick();
		
		// Ensure mouse helper(Mods like Flan's Mod may change mouse helper in certain conditions)
		// TODO: maybe do a wrapper if the mouse help is not the original one and also not this one
		MCWBClient.MC.mouseHelper = this.mouseHelper;
	}
	
	public boolean onHandRender()
	{
		// Check if hand should be rendered or not
		// Copied from {@link EntityRenderer#renderHand(float, int)}
		final Minecraft mc = MCWBClient.MC;
		final GameSettings settings = MCWBClient.SETTINGS;
		final Entity entity = mc.getRenderViewEntity();
		if(
			settings.thirdPersonView != 0
			|| (
				entity instanceof EntityLivingBase
				&& ( ( EntityLivingBase ) entity ).isPlayerSleeping()
			)
			|| settings.hideGUI
			|| mc.playerController.isSpectator()
			|| (
				this.prevMainItem.onHandRender( EnumHand.MAIN_HAND )
				&& this.prevOffItem.onHandRender( EnumHand.OFF_HAND )
			)
		) return true;
		
		// Otherwise, setup orientation for vanilla item rendering
		GL11.glRotatef( this.camRot.z, 0F, 0F, 1F );
		GL11.glRotatef( this.camRot.x, 1F, 0F, 0F );
		GL11.glRotatef( this.camRot.y - this.player.rotationYaw, 0F, 1F, 0F );
		GL11.glRotatef( -this.player.rotationPitch, 1F, 0F, 0F );
		return false;
	}
	
	public boolean onSpecificHandRender( EnumHand hand )
	{
		final IContextedItem it = hand == EnumHand.MAIN_HAND ? this.prevMainItem : this.prevOffItem;
		return it.onSpecificHandRender( hand );
	}
	
	public void onCameraSetup( CameraSetup evt )
	{
		this.cameraController.getCameraRot( this.camRot );
		evt.setYaw( 180F + this.camRot.y );
		evt.setPitch( this.camRot.x );
		evt.setRoll( this.camRot.z );
	}
	
	public boolean hideCrosshair() { return this.prevMainItem.hideCrosshair(); }
	
	public boolean onMouseWheelInput( int dWheel ) { return false; } // TODO
	
	public void onKeyInput( IKeyBind key ) { this.prevMainItem.onKeyInput( key ); }
	
	@Override
	public boolean hasCapability( Capability< ? > capability, EnumFacing facing ) {
		return capability == CAPABILITY || capability == PlayerPatch.CAPABILITY;
	}
}
