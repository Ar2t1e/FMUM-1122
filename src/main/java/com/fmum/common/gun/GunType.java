package com.fmum.common.gun;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.fmum.client.camera.ICameraController;
import com.fmum.client.gun.IEquippedGunPartRenderer;
import com.fmum.client.gun.IGunPartRenderer;
import com.fmum.client.input.IInput;
import com.fmum.client.input.Key;
import com.fmum.client.item.IItemModel;
import com.fmum.client.player.OperationClient;
import com.fmum.client.player.PlayerPatchClient;
import com.fmum.client.render.IAnimator;
import com.fmum.common.ammo.IAmmoType;
import com.fmum.common.item.IEquippedItem;
import com.fmum.common.item.IItem;
import com.fmum.common.item.IItemTypeHost;
import com.fmum.common.load.IContentProvider;
import com.fmum.common.meta.IMeta;
import com.fmum.common.module.IModuleEventSubscriber;
import com.fmum.common.network.PacketNotifyItem;
import com.fmum.common.player.IOperation;
import com.fmum.common.player.IOperationController;
import com.fmum.common.player.Operation;
import com.fmum.common.player.OperationController;
import com.fmum.common.player.PlayerPatch;
import com.fmum.util.Animation;
import com.fmum.util.ArmTracker;
import com.google.gson.annotations.SerializedName;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class GunType<
	I extends IGunPart< ? extends I >,
	C extends IGun< ? >,
	E extends IEquippedGun< ? extends C >,
	ER extends IEquippedGunPartRenderer< ? super E >,
	R extends IGunPartRenderer< ? super C, ? extends ER >,
	M extends IItemModel< ? extends R >
> extends GunPartType< I, C, E, ER, R, M >
{
	protected static final OperationController
		LOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new float[] { 0.8F },
			OperationController.NO_SPECIFIED_EFFECT,
			new float[] { 0.8F },
			"load_mag"
		),
		UNLOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new float[] { 0.5F },
			OperationController.NO_SPECIFIED_EFFECT,
			new float[] { 0.5F },
			"unload_mag"
		),
		CHARGE_GUN_CONTROLLER = new OperationController(
			1F / 22F,
			new float[] { 0.5F },
			OperationController.NO_SPECIFIED_EFFECT,
			OperationController.NO_KEY_TIME
		),
		INSPECT_CONTROLLER = new OperationController( 1F / 40F );
	
	@SerializedName( value = "isOpenBolt", alternate = "openBolt" )
	protected boolean isOpenBolt = false;
	
	protected boolean catchBoltOnEmpty = false;
	
	protected IOperationController
		loadMagController   = LOAD_MAG_CONTROLLER,
		unloadMagController = UNLOAD_MAG_CONTROLLER,
		chargeGunController = CHARGE_GUN_CONTROLLER,
		inspectController   = INSPECT_CONTROLLER;
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		provider.clientOnly( () -> {
			this.loadMagController.loadAnimation( provider );
			this.unloadMagController.loadAnimation( provider );
			this.chargeGunController.loadAnimation( provider );
			this.inspectController.loadAnimation( provider );
		} );
		return this;
	}
	
	protected abstract class Gun extends GunPart implements IGun< I >
	{
		protected transient IGunPart< ? > leftHandHolding = this;
		protected transient IGunPart< ? > rightHandHolding = this;
		
		protected IGunState state = this.createState();
		protected IGunState createState() {
			return GunType.this.isOpenBolt ? new StateCloseBolt() : new StateBoltRelease();
		}
		
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
		public void chargeGun()
		{
			
		}
		
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
		
		protected class StateOpenBolt implements IGunState
		{
			@Override
			public IGunState charge( EntityPlayer player ) { return this; }
		}
		
		protected class StateCloseBolt implements IGunState
		{
			@Override
			public IGunState charge( EntityPlayer player ) {
				return new StateOpenBolt(); // TODO: Check if creating inner without Gun.this.new keeps current instance.
			}
		}
		
		protected class StateBoltRelease implements IGunState
		{
			@Override
			public IGunState charge( EntityPlayer player )
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean noMag = mag == null;
				if ( noMag ) { return this; }
				
				final boolean hasAmmo = !mag.isEmpty();
				if ( hasAmmo ) { return new StateShootReady( mag.popAmmo() ); }
				
				return GunType.this.catchBoltOnEmpty ? new StateBoltCatch() : this;
			}
		}
		
		protected class StateBoltCatch implements IGunState
		{
			@Override
			public IGunState charge( EntityPlayer player )
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean noMag = mag == null;
				if ( noMag ) { return new StateBoltRelease(); }
				
				final boolean hasAmmo = !mag.isEmpty();
				return hasAmmo ? new StateShootReady( mag.popAmmo() ) : this;
			}
		}
		
		protected class StateShootReady implements IGunState
		{
			protected IAmmoType ammoInChamber;
			
			protected StateShootReady( IAmmoType ammo ) { this.ammoInChamber = ammo; }
			
			@Override
			public IGunState charge( EntityPlayer player )
			{
				if ( this.ammoInChamber != null )
				{
					// Eject it.
					final int amount = 1;
					player.dropItem( this.ammoInChamber.item(), amount );
				}
				
				final IMag< ? > mag = Gun.this.mag();
				final boolean noMag = mag == null;
				if ( noMag ) { return new StateBoltRelease(); }
				
				final boolean hasAmmo = !mag.isEmpty();
				if ( hasAmmo )
				{
					this.ammoInChamber = mag.popAmmo();
					return this;
				}
				
				return(
					GunType.this.catchBoltOnEmpty
					? new StateBoltCatch()
					: new StateBoltRelease()
				);
			}
		}
		
		protected class EquippedGun extends EquippedGunPart implements IEquippedGun< C >
		{
			protected static final byte
				OP_CODE_LOAD_MAG = 0,
				OP_CODE_UNLOAD_MAG = 1,
				OP_CODE_CHARGE_GUN = 2;
			
			protected EquippedGun(
				Supplier< ER > equippedRenderer,
				Supplier< Function< E, E > > renderDelegate,
				EntityPlayer player,
				EnumHand hand
			) { super( equippedRenderer, renderDelegate, player, hand ); }
			
			@Override
			public void handlePacket( ByteBuf buf, EntityPlayer player )
			{
				switch ( buf.readByte() )
				{
				case OP_CODE_UNLOAD_MAG:
					PlayerPatch.get( player ).launch( new OpUnloadMag() );
				break;
				
				case OP_CODE_LOAD_MAG:
					final int magInvSlot = buf.readByte();
					PlayerPatch.get( player ).launch( new OpLoadMag( magInvSlot ) );
				break;
				}
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			@SuppressWarnings( "unchecked" )
			protected void setupInputCallbacks( Map< IInput, Runnable > registry )
			{
				super.setupInputCallbacks( registry );
				
				final Runnable loadUnloadMag = () -> {
					final boolean shouldUnloadMag = Gun.this.hasMag();
					PlayerPatchClient.instance.launch(
						shouldUnloadMag ? new OpUnloadMagClient() : new OpLoadMagClient()
					);
				};
				registry.put( Key.LOAD_UNLOAD_MAG, loadUnloadMag );
				registry.put( Key.CO_LOAD_UNLOAD_MAG, loadUnloadMag );
				
				final Runnable chargeGun = () -> PlayerPatchClient.instance.launch(
					new OperationClient< EquippedGun >( this, GunType.this.chargeGunController )
					{
						@Override
						public IOperation launch( EntityPlayer player )
						{
							this.equipped.renderer.useAnimation( this.controller.animation() );
							this.equipped.renderDelegate = ori -> ( E ) EquippedGun.this;
							
							final ICameraController camera = PlayerPatchClient.instance.camera;
							camera.useAnimation( this.equipped.animator() );
							return this;
						}
						
						@Override
						protected void endCallback()
						{
							this.equipped.renderDelegate = original -> original;
							this.equipped.renderer.useAnimation( Animation.NONE );
						}
					}
				);
				registry.put( Key.CHARGE_GUN, chargeGun );
				registry.put( Key.CO_CHARGE_GUN, chargeGun );
				
				final Runnable inspectWeapon = () -> PlayerPatchClient.instance.launch(
					new OperationClient< EquippedGun >( this, GunType.this.inspectController )
					{
						@Override
						public IOperation launch( EntityPlayer player )
						{
							this.equipped.renderer.useAnimation( this.controller.animation() );
							final ICameraController camera = PlayerPatchClient.instance.camera;
							camera.useAnimation( this.equipped.animator() );
							return this;
						}
						
						@Override
						protected void endCallback() {
							this.equipped.renderer.useAnimation( Animation.NONE );
						}
					}
				);
				registry.put( Key.INSPECT, inspectWeapon );
				registry.put( Key.CO_INSPECT, inspectWeapon );
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
			
			@SideOnly( Side.CLIENT )
			protected class OperationOnGunClient extends OperationClient< EquippedGun >
			{
				protected OperationOnGunClient( IOperationController controller ) {
					super( EquippedGun.this, controller );
				}
				
				@Override
				@SuppressWarnings( "unchecked" )
				public IOperation launch( EntityPlayer player )
				{
					this.equipped.renderer.useAnimation( this.controller.animation() );
					this.equipped.renderDelegate = ori -> ( E ) EquippedGun.this;
					
					final ICameraController camera = PlayerPatchClient.instance.camera;
					camera.useAnimation( this.equipped.animator() );
					return this;
				}
				
				@Override
				protected void endCallback()
				{
					this.equipped.renderDelegate = original -> original;
					this.equipped.renderer.useAnimation( Animation.NONE );
				}
			}
			
			@SideOnly( Side.CLIENT )
			protected class OpUnloadMagClient extends OperationOnGunClient
			{
				protected OpUnloadMagClient() { super( GunType.this.unloadMagController ); }
				
				@Override
				public IOperation launch( EntityPlayer player )
				{
					final byte code = OP_CODE_UNLOAD_MAG;
					this.sendPacketToServer( new PacketNotifyItem( buf -> buf.writeByte( code ) ) );
					return super.launch( player );
				}
			}
			
			@SideOnly( Side.CLIENT )
			protected class OpLoadMagClient extends OperationOnGunClient
			{
				protected OpLoadMagClient() { super( GunType.this.loadMagController ); }
				
				@Override
				@SuppressWarnings( "unchecked" )
				public IOperation launch( EntityPlayer player )
				{
					final InventoryPlayer inv = player.inventory;
					final int invSlot = this.findValidMagInvSlot( inv );
					final boolean noValidMagToLoad = invSlot == -1;
					if ( noValidMagToLoad ) { return IOperation.NONE; }
					
					// Play animation.
					this.equipped.renderer.useAnimation( this.controller.animation() );
					final ICameraController camera = PlayerPatchClient.instance.camera;
					camera.useAnimation( this.equipped.animator() );
					
					// Copy this gun to install the loading mag.
					final EquippedGun copied = ( EquippedGun ) this.equipped.copy();
					final C copiedGun = copied.item();
					
					// Install the loading mag to render it. Copy before use.
					final ItemStack stack = inv.getStackInSlot( invSlot ).copy();
					final IMag< ? > mag = ( IMag< ? > ) IItemTypeHost.getItem( stack );
					copiedGun.loadMag( mag );
					mag.setAsLoadingMag();
					
					// Delegate render to copied gun.
					this.equipped.renderDelegate = ori -> ( E ) copied;
					
					// Send out packet!
					this.sendPacketToServer( new PacketNotifyItem( buf -> {
						buf.writeByte( OP_CODE_LOAD_MAG );
						buf.writeByte( invSlot );
					} ) );
					return this;
				}
				
				protected int findValidMagInvSlot( IInventory inv )
				{
					final int size = inv.getSizeInventory();
					for ( int i = 0; i < size; ++i )
					{
						final ItemStack stack = inv.getStackInSlot( i );
						final IItem item = IItemTypeHost.getItemOrDefault( stack );
						final boolean isMag = item instanceof IMag< ? >;
						final boolean isValidMag = isMag && Gun.this.isAllowed( ( IMag< ? > ) item );
						if ( isValidMag ) { return i; }
					}
					return -1;
				}
			}
			
			@SideOnly( Side.CLIENT )
			protected class OpChargeGunClient extends OperationOnGunClient
			{
				protected OpChargeGunClient() { super( GunType.this.chargeGunController ); }
				
				@Override
				public IOperation launch( EntityPlayer player )
				{
					final byte code = OP_CODE_CHARGE_GUN;
					this.sendPacketToServer( new PacketNotifyItem( buf -> buf.writeByte( code ) ) );
					return super.launch( player );
				}
			}
		}
		
		protected class OperationOnGun extends Operation
		{
			protected OperationOnGun( IOperationController controller ) { super( controller ); }
			
			protected Gun gun = Gun.this;
			
			@Override
			@SuppressWarnings( "unchecked" )
			public IOperation onStackUpdate( IEquippedItem< ? > newEquipped, EntityPlayer player )
			{
				this.gun = ( Gun ) newEquipped.item();
				return this;
			}
		}
		
		protected class OpLoadMag extends OperationOnGun
		{
			protected final int magInvSlot;
			
			protected OpLoadMag( int magInvSlot )
			{
				super( GunType.this.loadMagController );
				
				this.magInvSlot = magInvSlot;
			}
			
			@Override
			public IOperation launch( EntityPlayer player )
			{
				final boolean alreadyHasMag = this.gun.hasMag();
				if ( alreadyHasMag ) { return IOperation.NONE; }
				
				final ItemStack stack = player.inventory.getStackInSlot( this.magInvSlot );
				final IItem item = IItemTypeHost.getItemOrDefault( stack );
				final boolean isMag = item instanceof IMag< ? >;
				final boolean isValidMag = isMag && this.gun.isAllowed( ( IMag< ? > ) item );
				return isValidMag ? this : IOperation.NONE;
			}
			
			@Override
			protected void doHandleEffect( EntityPlayer player )
			{
				final InventoryPlayer inv = player.inventory;
				final ItemStack stack = inv.getStackInSlot( this.magInvSlot );
				final IItem item = IItemTypeHost.getItemOrDefault( stack );
				final boolean isMag = item instanceof IMag< ? >;
				if ( !isMag ) { return; }
				
				final IMag< ? > mag = ( IMag< ? > ) item;
				if ( !this.gun.isAllowed( mag ) ) { return; }
				
				this.gun.loadMag( mag );
				inv.setInventorySlotContents( this.magInvSlot, ItemStack.EMPTY );
			}
		}
		
		protected class OpUnloadMag extends OperationOnGun
		{
			protected OpUnloadMag() { super( GunType.this.unloadMagController ); }
			
			@Override
			public IOperation launch( EntityPlayer player ) {
				return this.gun.hasMag() ? this : IOperation.NONE;
			}
			
			@Override
			protected void doHandleEffect( EntityPlayer player ) {
				player.addItemStackToInventory( this.gun.unloadMag().toStack() );
			}
		}
	}
	
	protected interface IGunState
	{
		IGunState charge( EntityPlayer player );
	}
}
