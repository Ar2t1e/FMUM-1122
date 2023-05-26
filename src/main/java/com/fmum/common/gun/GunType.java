package com.fmum.common.gun;

import com.fmum.client.camera.ICameraController;
import com.fmum.client.gun.IEquippedGunRenderer;
import com.fmum.client.gun.IGunPartRenderer;
import com.fmum.client.input.IInput;
import com.fmum.client.input.Key;
import com.fmum.client.item.IItemModel;
import com.fmum.client.player.OpModifyClient;
import com.fmum.client.player.OperationClient;
import com.fmum.client.player.PlayerPatchClient;
import com.fmum.client.render.CoupledAnimation;
import com.fmum.client.render.IAnimator;
import com.fmum.client.render.ReadOnlyAnimator;
import com.fmum.common.ammo.IAmmoType;
import com.fmum.common.item.IEquippedItem;
import com.fmum.common.item.IItem;
import com.fmum.common.item.IItemTypeHost;
import com.fmum.common.load.IContentProvider;
import com.fmum.common.mag.IMag;
import com.fmum.common.meta.IMeta;
import com.fmum.common.module.IModuleEventSubscriber;
import com.fmum.common.network.PacketNotifyItem;
import com.fmum.common.player.IOperation;
import com.fmum.common.player.Operation;
import com.fmum.common.player.OperationController;
import com.fmum.common.player.OperationController.TimedEffect;
import com.fmum.common.player.OperationController.TimedSound;
import com.fmum.common.player.PlayerPatch;
import com.fmum.util.Animation;
import com.fmum.util.ArmTracker;
import com.google.gson.annotations.SerializedName;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class GunType<
	I extends IGunPart< ? extends I >,
	C extends IGun< ? >,
	E extends IEquippedGun< ? extends C >,
	ER extends IEquippedGunRenderer< ? super E >,
	R extends IGunPartRenderer< ? super C, ? extends ER >,
	M extends IItemModel< ? extends R >
> extends GunPartType< I, C, E, ER, R, M >
{
	protected static final OperationController
		LOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new TimedEffect[] { new TimedEffect( 0.8F, "loadMag" ) },
			new TimedSound[] { new TimedSound( 0.8F, "load_mag" ) }
		),
		UNLOAD_MAG_CONTROLLER = new OperationController(
			1F / 40F,
			new TimedEffect[] { new TimedEffect( 0.5F, "unloadMag" ) },
			new TimedSound[] { new TimedSound( 0.5F, "unload_mag" ) }
		);
	
	protected static final ControllerDispatcher
		CHARGE_CONTROLLER_DISPATCHER = new ControllerDispatcher(
			new GunOpController(
				1F / 22F,
				new TimedEffect[] { new TimedEffect( 0.5F, "charge" ) },
				new TimedSound[] { },
				false
			)
		),
		INSPECT_CONTROLLER_DISPATCHER = new ControllerDispatcher( new GunOpController( 1F / 40F ) );
	
//	protected static final IFireController[] DEFAULT_FIRE_CONTROLLERS = { new FullAuto() };
//
//	@SerializedName( value = "fireControllers", alternate = "fireModes" )
//	protected transient IFireController[] fireControllers;
	
	protected SoundEvent shootSound;
	
	@SerializedName( value = "isOpenBolt", alternate = "openBolt" )
	protected boolean isOpenBolt = false;
	
	protected boolean catchBoltOnEmpty = false;
	
	@SideOnly( Side.CLIENT )
	@SerializedName( value = "staticBoltRelease", alternate = "staticBoltClose" )
	protected Animation staticBoltRelease;
	
	@SideOnly( Side.CLIENT )
	@SerializedName( value = "staticBoltCatch", alternate = "staticBoltOpen" )
	protected Animation staticBoltCatch;
	
	@SideOnly( Side.CLIENT )
	protected Animation shootAnimation;
	
	@SideOnly( Side.CLIENT )
	protected Animation shootBoltCatchAnimation;
	
	protected OperationController loadMagController = LOAD_MAG_CONTROLLER;
	protected OperationController unloadMagController = UNLOAD_MAG_CONTROLLER;
	
	protected ControllerDispatcher chargeControllerDispatcher = CHARGE_CONTROLLER_DISPATCHER;
	
	protected ControllerDispatcher inspectControllerDispatcher = INSPECT_CONTROLLER_DISPATCHER;
	
	@Override
	public IMeta build( String name, IContentProvider provider )
	{
		super.build( name, provider );
		
		provider.clientOnly( () -> {
			this.loadMagController.checkAssetsSetup( provider );
			this.unloadMagController.checkAssetsSetup( provider );
			this.chargeControllerDispatcher.checkAssetsSetup( provider );
			this.inspectControllerDispatcher.checkAssetsSetup( provider );
			
			this.staticBoltRelease = Optional
				.ofNullable( this.staticBoltRelease ).orElse( Animation.NONE );
			this.staticBoltCatch = Optional
				.ofNullable( this.staticBoltCatch ).orElse( Animation.NONE );
			this.shootAnimation = Optional
				.ofNullable( this.shootAnimation ).orElse( Animation.NONE );
			this.shootBoltCatchAnimation = Optional
				.ofNullable( this.shootBoltCatchAnimation ).orElse( Animation.NONE );
		} );
		return this;
	}
	
	protected abstract class Gun extends GunPart implements IGun< I >
	{
		protected transient IGunPart< ? > leftHandHolding = this;
		protected transient IGunPart< ? > rightHandHolding = this;
		
		protected IGunState state;
		
		protected IFireController fireController;
		protected int roundsShot;
		
		protected Gun()
		{
			this.state = this.createGunState();
//			this.fireController = GunType.this.fireControllers[ 0 ];
			
			final int[] data = Gun.this.nbt.getIntArray( DATA_TAG );
			final int baseIdx = super.dataSize();
			data[ baseIdx + 0 ] = this.state.toAmmoIdAndOrdinal();
			
			final int roundsShot = 0;
			data[ baseIdx + 1 ] = roundsShot;
		}
		
		protected Gun( boolean ignoredWaitForDeserialize ) { super( ignoredWaitForDeserialize ); }
		
		@Override
		public boolean hasMag() { return this.getInstalledCount( 0 ) > 0; }
		
		@Nullable
		@Override
		public IMag< ? > mag() {
			return this.hasMag() ? (com.fmum.common.mag.IMag< ? > ) this.getInstalled( 0, 0 ) : null;
		}
		
		@Override
		public boolean isAllowed( IMag< ? > mag ) {
			return GunType.this.slots.get( 0 ).isAllowed( mag );
		}
		
		@Override
		public void loadMag( IMag< ? > mag ) { this.install( 0, mag ); }
		
		@Override
		public IMag< ? > unloadMag() { return (com.fmum.common.mag.IMag< ? > ) this.remove( 0, 0 ); }
		
		@Override
		public void chargeGun( EntityPlayer player )
		{
			this.state = this.state.charge( player );
			this.syncGunState();
		}
		
		@Override
		public void updateModuleState(
			BiConsumer< Class< ? >, IModuleEventSubscriber< ? > > registry
		) {
			super.updateModuleState( registry );
			
			this.leftHandHolding = this;
			this.rightHandHolding = this;
			this.forEachModule( gunPart -> {
				if ( gunPart.leftHandPriority() > this.leftHandHolding.leftHandPriority() ) {
					this.leftHandHolding = gunPart;
				}
				if ( gunPart.rightHandPriority() > this.rightHandHolding.rightHandPriority() ) {
					this.rightHandHolding = gunPart;
				}
			} );
		}
		
		@Override
		public void forEachAmmo( Consumer< IAmmoType > visitor ) {
			this.state.forEachAmmo( visitor );
		}
		
		@Override
		public void deserializeNBT( NBTTagCompound nbt )
		{
			super.deserializeNBT( nbt );
			
			final int baseIdx = super.dataSize();
			
			// Weapon state and ammo in barrel.
			final int[] data = Gun.this.nbt.getIntArray( DATA_TAG );
			final int value = data[ baseIdx + 0 ];
			final int stateOrdinary = 0xFFFF & value;
			switch ( stateOrdinary )
			{
			case StateBoltOpen.ORDINAL:
				this.state = new StateBoltOpen();
				break;
			
			case StateBoltClose.ORDINAL:
				this.state = new StateBoltClose();
				break;
			
			case StateBoltRelease.ORDINAL:
				this.state = new StateBoltRelease();
				break;
			
			case StateBoltCatch.ORDINAL:
				this.state = new StateBoltCatch();
				break;
			
			case StateShootReady.ORDINAL:
				final Item ammoItem = Item.getItemById( value >>> 16 );
				final IAmmoType ammo = ( IAmmoType ) IItemTypeHost.getType( ammoItem );
				this.state = new StateShootReady( ammo );
				break;
			}
			
			// Rounds shot and fire controller index.
			final int val = data[ baseIdx + 1 ];
			this.roundsShot = val >>> 16;
//			this.fireController = GunType.this.fireControllers[ 0xFFFF & val ];
		}
		
		// 0 -> 16-bit ammo id     | 16-bit state;
		// 1 -> 16-bit rounds shot | 16-bit fire controller index;
		@Override
		protected int dataSize() { return super.dataSize() + 2; }
		
		protected IGunState createGunState() {
			return GunType.this.isOpenBolt ? new StateBoltClose() : new StateBoltRelease();
		}
		
		protected final void syncGunState()
		{
			final int[] data = Gun.this.nbt.getIntArray( DATA_TAG );
			data[ super.dataSize() ] = this.state.toAmmoIdAndOrdinal();
			this.syncAndUpdate();
		}
		
		protected class EquippedGun extends EquippedGunPart implements IEquippedGun< C >
		{
			protected static final byte
				OP_CODE_LOAD_MAG = 0,
				OP_CODE_UNLOAD_MAG = 1,
				OP_CODE_CHARGE_GUN = 2;
			
			protected int actionCoolDown = 0;
			
			protected ITriggerHandler triggerHandler = ITriggerHandler.NONE;
			
			protected EquippedGun( EntityPlayer player, EnumHand hand )
			{
				super( player, hand );
				
				if ( player.world.isRemote )
				{
					this.renderer.useGunAnimation(
						new CoupledAnimation( Gun.this.state.staticAnimation(), () -> 1F )
					);
				}
			}
			
			@SuppressWarnings( "unchecked" )
			protected EquippedGun(
				IEquippedItem< ? > prevEquipped,
				EntityPlayer player,
				EnumHand hand
			) {
				super( prevEquipped, player, hand );
				
				final EquippedGun prev = ( EquippedGun ) prevEquipped;
				
				this.actionCoolDown = prev.actionCoolDown;
				if ( player.world.isRemote ) {
					this.triggerHandler = prev.triggerHandler;
				}
			}
			
			@Override
			public void tickInHand( EntityPlayer player, EnumHand hand )
			{
				super.tickInHand( player, hand );
				
				this.triggerHandler = this.triggerHandler.tick( player );
			}
			
			@Override
			public void handlePacket( ByteBuf buf, EntityPlayer player )
			{
				switch ( buf.readByte() )
				{
				case OP_CODE_CHARGE_GUN:
					PlayerPatch.get( player ).launch( new OpChargeGun() );
					break;
				
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
			public void onKeyPress( IInput key )
			{
//				final boolean pullTrigger = key == Key.PULL_TRIGGER;
//				if ( pullTrigger )
//				{
//					final int actionRounds = Gun.this.fireController.actionRounds();
//					if ( actionRounds == 0 ) { return; }
//					
//					// We can pull trigger down now. // TODO: Play sound.
//					
//					
//					// Delegate render to buffered instance.
//					final E copied = ( E ) this.copy();
//					EquippedGun.this.renderDelegate = ori -> copied;
//					
//					final ITriggerHandler handler = new ITriggerHandler()
//					{
//						int bufferedShotCount = Gun.this.roundsShot;
//						int actedRounds = 0;
//						int coolDown = 0;
//						
//						@Override
//						public ITriggerHandler tick( EntityPlayer player )
//						{
//							// If we are the copied one.
//							while ( this.coolDown == 0 )
//							{
//								final ShootResult result = Gun.this.state.tryShoot(
//									this.actedRounds,
//									this.bufferedShotCount,
//									FMUMClient.MC.player
//								);
//								this.coolDown = result.actionDuration;
////								EquippedGun.this.renderer.useAnimation( result.actionAnimation );
//								Gun.this.state = result.newState;
//								
//								this.bufferedShotCount = result.shotCount;
//								this.actedRounds = result.actedRounds;
//							}
//							
//							this.coolDown -= 1;
//							return this.actedRounds == actionRounds ? ITriggerHandler.NONE : this;
//						}
//						
//						@Override
//						public ITriggerHandler onTriggerRelease()
//						{
//							this.actedRounds = Gun.this.fireController.releaseRounds( this.actedRounds );
//							return this.actedRounds == actionRounds ? ITriggerHandler.NONE : this; // TODO: keep buffered rounds for a while
//						}
//						
//						@Override
//						public int getRoundsShot( int rawRoundsShot ) {
//							return this.bufferedShotCount;
//						}
//					};
//					return;
//				}
				
				final boolean loadUnloadMag = (
					key == Key.LOAD_UNLOAD_MAG
					|| key == Key.CO_LOAD_UNLOAD_MAG
				);
				if ( loadUnloadMag )
				{
					PlayerPatchClient.instance.launch(
						Gun.this.hasMag() ? new OpUnloadMagClient() : new OpLoadMagClient()
					);
					return;
				}
				
				final boolean chargeGun = key == Key.CHARGE_GUN || key == Key.CO_CHARGE_GUN;
				if ( chargeGun )
				{
					PlayerPatchClient.instance.launch( new OpChargeGunClient() );
					return;
				}
				
				final boolean inspectWeapon = key == Key.INSPECT || key == Key.CO_INSPECT;
				if ( inspectWeapon )
				{
					final boolean boltCatch = Gun.this.state.boltCatch();
					PlayerPatchClient.instance.launch( new OperationOnGunClient(
						GunType.this.inspectControllerDispatcher
							.match( Gun.this.mag(), boltCatch, boltCatch )
					) );
					return;
				}
				
				super.onKeyPress( key );
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public boolean updateViewBobbing( boolean original ) { return false; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public boolean hideCrosshair()
			{
				final IOperation executing = PlayerPatchClient.instance.executing();
				final boolean modifying = executing instanceof OpModifyClient;
				final boolean freeView = Key.FREE_VIEW.down || Key.CO_FREE_VIEW.down;
				return !( modifying && freeView );
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public void setupRenderArm(
				IAnimator animator,
				ArmTracker leftArm, ArmTracker rightArm
			) {
				// TODO: Move to this maybe?
				Gun.this.leftHandHolding.setupLeftArmToRender( animator, leftArm );
				Gun.this.rightHandHolding.setupRightArmToRender( animator, rightArm );
			}
			
			@SideOnly( Side.CLIENT )
			protected final Gun inner() { return Gun.this; }
			
			@SideOnly( Side.CLIENT )
			protected class OperationOnGunClient extends OperationClient< EquippedGun >
			{
				protected OperationOnGunClient( OperationController controller ) {
					super( EquippedGun.this, controller );
				}
				
				@Override
				@SuppressWarnings( "unchecked" )
				public IOperation launch( EntityPlayer player )
				{
					this.equipped.renderer.useOperateAnimation(
						new CoupledAnimation(
							this.controller.animation(),
							this::smoothedProgress
						)
					);
					this.equipped.renderDelegate = ori -> ( E ) EquippedGun.this;
					
					final ICameraController camera = PlayerPatchClient.instance.camera;
					camera.useAnimation( new ReadOnlyAnimator( this.equipped.animator() ) );
					return this;
				}
				
				@Override
				protected void endCallback()
				{
					this.equipped.renderDelegate = original -> original;
					this.equipped.renderer.useOperateAnimation( IAnimator.NONE );
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
					this.equipped.renderer.useOperateAnimation(
						new CoupledAnimation( this.controller.animation(), this::smoothedProgress )
					);
					final ICameraController camera = PlayerPatchClient.instance.camera;
					camera.useAnimation( new ReadOnlyAnimator( this.equipped.animator() ) );
					
					// Copy this gun to install the loading mag.
					final EquippedGun copied = ( EquippedGun ) this.equipped.copy();
					final C copiedGun = copied.item();
					
					// Install the loading mag to render it. // Copy before use.
					final ItemStack stack = inv.getStackInSlot( invSlot ).copy();
					final IMag< ? > mag = (com.fmum.common.mag.IMag< ? > ) IItemTypeHost.getItem( stack );
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
						final boolean isValidMag = isMag && Gun.this.isAllowed( (com.fmum.common.mag.IMag< ? > ) item );
						if ( isValidMag ) { return i; }
					}
					return -1;
				}
			}
			
			@SideOnly( Side.CLIENT )
			protected class OpChargeGunClient extends OperationOnGunClient
			{
				protected OpChargeGunClient() { super( Gun.this.state.chargeController() ); }
				
				@Override
				public IOperation launch( EntityPlayer player )
				{
					final byte code = OP_CODE_CHARGE_GUN;
					this.sendPacketToServer( new PacketNotifyItem( buf -> buf.writeByte( code ) ) );
					
					EquippedGun.this.renderer.useGunAnimation( IAnimator.NONE );
					return super.launch( player );
				}
				
				@Override
				protected void endCallback()
				{
					super.endCallback();
					
					EquippedGun.this.renderer.useGunAnimation( new CoupledAnimation(
						this.equipped.inner().state.staticAnimation(),
						() -> 1F
					) );
				}
			}
		}
		
		protected class StateBoltOpen implements IGunState
		{
			protected static final int ORDINAL = 0;
			
			@Override
			public OperationController chargeController()
			{
				final boolean boltOpenBeforeAction = true;
				final boolean boltOpenAfterAction = true;
				return GunType.this.chargeControllerDispatcher.match(
					Gun.this.mag(), boltOpenBeforeAction, boltOpenAfterAction
				);
			}
			
			@Override
			public boolean boltCatch() { return true; }
			
			@Override
			public IGunState charge( EntityPlayer player ) { return this; }
			
			@Override
			public int toAmmoIdAndOrdinal() { return ORDINAL; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public Animation staticAnimation() { return GunType.this.staticBoltCatch; }
		}
		
		protected class StateBoltClose implements IGunState
		{
			protected static final int ORDINAL = 1;
			
			@Override
			public OperationController chargeController()
			{
				final boolean boltOpenBeforeAction = false;
				final boolean boltOpenAfterAction = true;
				return GunType.this.chargeControllerDispatcher.match(
					Gun.this.mag(), boltOpenBeforeAction, boltOpenAfterAction
				);
			}
			
			@Override
			public IGunState charge( EntityPlayer player ) {
				return new StateBoltOpen(); // TODO: Check if creating inner without Gun.this.new keeps current instance.
			}
			
			@Override
			public int toAmmoIdAndOrdinal() { return ORDINAL; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public Animation staticAnimation() { return GunType.this.staticBoltRelease; }
		}
		
		protected class StateBoltRelease implements IGunState
		{
			protected static final int ORDINAL = 2;
			
			@Override
			public OperationController chargeController()
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean boltCatchBeforeAction = false;
				final boolean boltCatchAfterAction = mag != null
                    && mag.isEmpty() && GunType.this.catchBoltOnEmpty;
				return GunType.this.chargeControllerDispatcher.match(
					mag, boltCatchBeforeAction, boltCatchAfterAction
				);
			}
			
			@Override
			public IGunState charge( EntityPlayer player )
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean magDoesNotExit = mag == null;
				if ( magDoesNotExit ) { return this; }
				
				final boolean hasAmmo = !mag.isEmpty();
				if ( hasAmmo ) { return new StateShootReady( mag.popAmmo() ); }
				
				return GunType.this.catchBoltOnEmpty ? new StateBoltCatch() : this;
			}
			
			@Override
			public int toAmmoIdAndOrdinal() { return ORDINAL; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public Animation staticAnimation() { return GunType.this.staticBoltRelease; }
		}
		
		protected class StateBoltCatch implements IGunState
		{
			protected static final int ORDINAL = 3;
			
			@Override
			public OperationController chargeController()
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean boltCatchBeforeAction = true;
				final boolean boltCatchAfterAction = mag != null
                    && mag.isEmpty() && GunType.this.catchBoltOnEmpty;
				return GunType.this.chargeControllerDispatcher.match(
					mag, boltCatchBeforeAction, boltCatchAfterAction
				);
			}
			
			@Override
			public boolean boltCatch() { return true; }
			
			@Override
			public IGunState charge( EntityPlayer player )
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean noMag = mag == null;
				if ( noMag ) { return new StateBoltRelease(); }
				
				final boolean hasAmmo = !mag.isEmpty();
				return hasAmmo ? new StateShootReady( mag.popAmmo() ) : this;
			}
			
			@Override
			public int toAmmoIdAndOrdinal() { return ORDINAL; }
			
			@Override
			@SideOnly( Side.CLIENT )
			public Animation staticAnimation() { return GunType.this.staticBoltCatch; }
		}
		
		protected class StateShootReady implements IGunState
		{
			protected static final int ORDINAL = 4;
			
			protected IAmmoType ammoInChamber;
			
			protected StateShootReady( IAmmoType ammo ) { this.ammoInChamber = ammo; }
			
//			@Override
//			public ShootResult tryShoot( int actedRounds, int shotCount, EntityPlayer player )
//			{
//				player.world.playSound(
//					player.posX, player.posY, player.posZ,
//					GunType.this.shootSound,
//					SoundCategory.PLAYERS,
//					1F, 1F, false
//				);
//				return new ShootResult( newState, actionDuration, actionAnimation, actionRounds, shootCount );
//			}
			
			@Override
			public OperationController chargeController()
			{
				final IMag< ? > mag = Gun.this.mag();
				final boolean boltCatchBeforeAction = false;
				final boolean boltCatchAfterAction = mag != null
                    && mag.isEmpty() && GunType.this.catchBoltOnEmpty;
				return GunType.this.chargeControllerDispatcher.match(
					mag, boltCatchBeforeAction, boltCatchAfterAction
				);
			}
			
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
			
			@Override
			public void forEachAmmo( Consumer< IAmmoType > visitor ) {
				visitor.accept( this.ammoInChamber );
			}
			
			@Override
			public int toAmmoIdAndOrdinal() {
				return ORDINAL + ( Item.getIdFromItem( this.ammoInChamber.item() ) << 16 );
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public Animation staticAnimation() { return GunType.this.staticBoltRelease; }
		}
		
		protected class OperationOnGun extends Operation
		{
			protected OperationOnGun( OperationController controller ) { super( controller ); }
			
			protected IGun< ? > gun = Gun.this;
			
			@Override
			public IOperation onStackUpdate( IEquippedItem< ? > newEquipped, EntityPlayer player )
			{
				this.gun = ( IGun< ? > ) newEquipped.item();
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
				final boolean isValidMag = isMag && this.gun.isAllowed( (com.fmum.common.mag.IMag< ? > ) item );
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
				
				final IMag< ? > mag = (com.fmum.common.mag.IMag< ? > ) item;
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
		
		protected class OpChargeGun extends OperationOnGun
		{
			protected OpChargeGun() { super( Gun.this.state.chargeController() ); }
			
			@Override
			protected void doHandleEffect( EntityPlayer player ) { this.gun.chargeGun( player ); }
		}
	}
	
	protected interface IFireController
	{
		int actionRounds();
		
		int releaseRounds( int actedRounds );
	}
	
	protected interface IGunState
	{
		default boolean boltCatch() { return false; }
		
//		ShootResult tryShoot( int actedRounds, int shotNumber, EntityPlayer player );
		
		OperationController chargeController();
		
		IGunState charge( EntityPlayer player );
		
		default void forEachAmmo( Consumer< IAmmoType > visitor ) { }
		
		int toAmmoIdAndOrdinal();
		
		@SideOnly( Side.CLIENT )
		Animation staticAnimation();
	}
	
	protected interface ITriggerHandler
	{
		ITriggerHandler NONE = new ITriggerHandler()
		{
			@Override
			public ITriggerHandler tick( EntityPlayer player ) { return this; }
			
			@Override
			public ITriggerHandler onTriggerRelease() { return this; }
			
			@Override
			public int getRoundsShot( int rawRoundsShot ) { return rawRoundsShot; }
		};
		
		ITriggerHandler tick( EntityPlayer player );
		
		ITriggerHandler onTriggerRelease();
		
		int getRoundsShot( int rawRoundsShot );
	}
	
	protected static class ShootResult
	{
		public final IGunState newState;
		public final int actionDuration;
		public final Animation actionAnimation;
		public final int actedRounds;
		public final int shotCount;
		
		public ShootResult(
			IGunState newState,
			int actionDuration,
			Animation actionAnimation,
			int actionRounds,
			int shotCount
		) {
			this.newState = newState;
			this.actionDuration = actionDuration;
			this.actionAnimation = actionAnimation;
			this.actedRounds = actionRounds;
			this.shotCount = shotCount;
		}
	}
}
