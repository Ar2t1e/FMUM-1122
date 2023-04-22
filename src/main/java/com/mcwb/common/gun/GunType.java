package com.mcwb.common.gun;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.mcwb.client.MCWBClient;
import com.mcwb.client.gun.IGunPartRenderer;
import com.mcwb.client.input.IKeyBind;
import com.mcwb.client.input.Key;
import com.mcwb.client.item.IEquippedItemRenderer;
import com.mcwb.client.item.IItemModel;
import com.mcwb.client.player.OpLoadMagClient;
import com.mcwb.client.player.OpUnloadMagClient;
import com.mcwb.client.player.PlayerPatchClient;
import com.mcwb.client.render.IAnimator;
import com.mcwb.common.IAutowirePacketHandler;
import com.mcwb.common.MCWB;
import com.mcwb.common.item.IItemTypeHost;
import com.mcwb.common.load.IContentProvider;
import com.mcwb.common.meta.IMeta;
import com.mcwb.common.module.IModuleEventSubscriber;
import com.mcwb.common.network.PacketCode;
import com.mcwb.common.network.PacketCode.Code;
import com.mcwb.common.network.PacketNotifyItem;
import com.mcwb.common.operation.IOperation;
import com.mcwb.common.operation.IOperationController;
import com.mcwb.common.operation.OperationController;
import com.mcwb.common.player.OpLoadMag;
import com.mcwb.common.player.OpUnloadMag;
import com.mcwb.common.player.PlayerPatch;
import com.mcwb.util.Animation;
import com.mcwb.util.ArmTracker;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class GunType<
	I extends IGunPart< ? extends I >,
	C extends IGun< ? >,
	E extends IEquippedGun< ? extends C >,
	ER extends IEquippedItemRenderer< ? super E >,
	R extends IGunPartRenderer< ? super C, ? extends ER >,
	M extends IItemModel< ? extends R >
> extends GunPartType< I, C, E, ER, R, M >
{
	protected static final OperationController
		LOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new float[] { 0.8F },
			new String[ 0 ],
			new float[] { 0.8F },
			"load_mag"
		),
		UNLOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new float[] { 0.5F },
			new String[ 0 ],
			new float[] { 0.5F },
			"unload_mag"
		);
	
	protected IOperationController loadMagController = LOAD_MAG_CONTROLLER;
	@SideOnly( Side.CLIENT )
	@SerializedName( value = "loadMagAnimation" )
	protected String loadMagAnimationPath;
	@SideOnly( Side.CLIENT )
	protected transient Animation loadMagAnimation;
	
	protected IOperationController unloadMagController = UNLOAD_MAG_CONTROLLER;
	@SideOnly( Side.CLIENT )
	@SerializedName( value = "unloadMagAnimation" )
	protected String unloadMagAnimationPath;
	@SideOnly( Side.CLIENT )
	protected transient Animation unloadMagAnimation;
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		provider.clientOnly( () -> {
			this.loadMagAnimation = provider.loadAnimation( this.loadMagAnimationPath );
			this.unloadMagAnimation = provider.loadAnimation( this.unloadMagAnimationPath );
		} );
		return this;
	}
	
	protected abstract class Gun extends GunPart implements IGun< I >
	{
		protected transient IGunPart< ? > leftHandHolding = this;
		protected transient IGunPart< ? > rightHandHolding = this;
		
		protected Gun() { }
		
		protected Gun( boolean unused ) { super( unused ); }
		
		@Override
		public boolean hasMag() { return this.getInstalledCount( 0 ) > 0; }
		
		@Nullable
		@Override
		public IMag< ? > mag() {
			return this.hasMag() ? ( IMag< ? > ) this.getInstalled( 0, 0 ) : null;
		}
		
		@Override
		public boolean isAllowed( IMag< ? > mag ) {
			return GunType.this.slots.get( 0 ).isAllowed( mag );
		}
		
		@Override
		public void loadMag( IMag< ? > mag ) { this.install( 0, mag ); }
		
		@Override
		public IMag< ? > unloadMag() { return ( IMag< ? > ) this.remove( 0, 0 ); }
		
		@Override
		public void updateState( BiConsumer< Class< ? >, IModuleEventSubscriber< ? > > registry )
		{
			super.updateState( registry );
			
			this.leftHandHolding = this;
			this.rightHandHolding = this;
			this.forEach( gunPart -> {
				if ( gunPart.leftHandPriority() > this.leftHandHolding.leftHandPriority() ) {
					this.leftHandHolding = gunPart;
				}
				if ( gunPart.rightHandPriority() > this.rightHandHolding.rightHandPriority() ) {
					this.rightHandHolding = gunPart;
				}
			} );
		}
		
		protected class EquippedGun extends EquippedGunPart
			implements IEquippedGun< C >, IAutowirePacketHandler
		{
			@SideOnly( Side.CLIENT )
			protected Runnable loadingMagRenderer;
			
			protected EquippedGun(
				Supplier< ER > equippedRenderer,
				Supplier< Function< E, E > > renderDelegate,
				EntityPlayer player,
				EnumHand hand
			) {
				super( equippedRenderer, renderDelegate, player, hand );
				
				MCWB.MOD.clientOnly( () -> this.loadingMagRenderer = () -> { } );
			}
			
			@Override
			public void handlePacket( ByteBuf buf, EntityPlayer player )
			{
				final byte[] messageBytes = new byte[ buf.readByte() ];
				buf.readBytes( messageBytes );
				final String message = new String( messageBytes );
				switch ( message )
				{
				case "unload":
					final IOperationController opController0 = GunType.this.unloadMagController;
					final IOperation op0 = new OpUnloadMag( this, opController0 );
					PlayerPatch.get( player ).tryLaunch( op0 );
					break;
					
				case "load":
					final int invSlot = buf.readByte();
					final IOperationController opController1 = GunType.this.loadMagController;
					final IOperation op1 = new OpLoadMag( this, opController1, invSlot );
					PlayerPatch.get( player ).tryLaunch( op1 );
					break;
				}
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			@SuppressWarnings( "unchecked" )
			public void onKeyPress( IKeyBind key )
			{
				switch ( key.name() )
				{
				case Key.LOAD_UNLOAD_MAG:
				case Key.CO_LOAD_UNLOAD_MAG:
					final Consumer< IEquippedGun< ? > > opTerminateCallback = equipped -> {
						final EquippedGun equippedGun = ( EquippedGun ) equipped;
						equippedGun.animator().playAnimation( Animation.NONE );
						equippedGun.renderDelegate = original -> original;
						this.sendPacketToServer( new PacketCode( Code.TERMINATE_OP ) );
					};
					PlayerPatchClient.instance.tryLaunch(
						Gun.this.hasMag()
						? new OpUnloadMagClient(
							this,
							GunType.this.unloadMagController,
							() -> {
								this.animator().playAnimation( GunType.this.unloadMagAnimation );
								EquippedGun.this.renderDelegate = original -> ( E ) EquippedGun.this;
								this.sendPacketToServer( new PacketNotifyItem( buf -> {
									final String message = "unload";
									buf.writeByte( message.length() );
									buf.writeBytes( message.getBytes() );
								} ) );
							},
							opTerminateCallback
						)
						: new OpLoadMagClient(
							this,
							GunType.this.loadMagController,
							invSlot -> {
								this.animator().playAnimation( GunType.this.loadMagAnimation );
								EquippedGun.this.renderDelegate = original -> ( E ) EquippedGun.this;
								
								// Install the loading mag to render it
								final InventoryPlayer inv = MCWBClient.MC.player.inventory;
								final ItemStack stack = inv.getStackInSlot( invSlot ).copy();
								final IMag< ? > mag = ( IMag< ? > ) IItemTypeHost.getItem( stack );
								Gun.this.install( 0, mag );
								mag.setAsLoadingMag();
								
								this.sendPacketToServer( new PacketNotifyItem( buf -> {
									final String message = "load";
									buf.writeByte( message.length() );
									buf.writeBytes( message.getBytes() );
									buf.writeByte( invSlot );
								} ) );
							},
							equipped -> {
								// If somehow the load is not completed then remove the loading mag
								if ( equipped == EquippedGun.this ) { Gun.this.remove( 0, 0 ); }
								opTerminateCallback.accept( equipped );
							}
						)
					);
					break;
					
				default: // TODO: super.onKeyPress( key );
				}
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public boolean updateViewBobbing( boolean original ) { return false; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public boolean hideCrosshair()
			{
//				final IOperation executing = PlayerPatchClient.instance.executing();
//				final boolean modifying = executing instanceof OpModifyClient;
//				final boolean freeView = InputHandler.FREE_VIEW.down || InputHandler.CO_FREE_VIEW.down;
//				return !( modifying && freeView );
				return true;
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public void setupRenderArm(
				IAnimator animator,
				ArmTracker leftArm,
				ArmTracker rightArm
			) {
				// TODO: Move to this maybe?
				Gun.this.leftHandHolding.setupLeftArmToRender( animator, leftArm );
				Gun.this.rightHandHolding.setupRightArmToRender( animator, rightArm );
			}
		}
	}
}
