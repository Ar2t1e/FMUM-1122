package com.mcwb.common.gun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.mcwb.client.gun.IGunPartRenderer;
import com.mcwb.client.input.IInput;
import com.mcwb.client.input.Key;
import com.mcwb.client.item.IEquippedItemRenderer;
import com.mcwb.client.item.IItemModel;
import com.mcwb.client.player.OpLoadAmmoClient;
import com.mcwb.client.player.OpUnloadAmmoClient;
import com.mcwb.client.player.PlayerPatchClient;
import com.mcwb.common.ammo.IAmmoType;
import com.mcwb.common.item.IItemTypeHost;
import com.mcwb.common.network.PacketNotifyItem;
import com.mcwb.common.operation.IOperation;
import com.mcwb.common.operation.IOperationController;
import com.mcwb.common.operation.OperationController;
import com.mcwb.common.player.OpLoadAmmo;
import com.mcwb.common.player.OpUnloadAmmo;
import com.mcwb.common.player.PlayerPatch;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class MagType<
	I extends IGunPart< ? extends I >,
	C extends IMag< ? >,
	E extends IEquippedMag< ? extends C >,
	ER extends IEquippedItemRenderer< ? super E >,
	R extends IGunPartRenderer< ? super C, ? extends ER >,
	M extends IItemModel< ? extends R >
> extends GunPartType< I, C, E, ER, R, M >
{
	protected static final OperationController
	LOAD_AMMO_CONTROLLER = new OperationController(
		1F / 10F,
		new float[] { 0.8F },
		new String[ 0 ],
		new float[] { 0.8F },
		"load_ammo"
	),
	UNLOAD_AMMO_CONTROLLER = new OperationController(
		1F / 8F,
		new float[] { 0.8F },
		new String[ 0 ],
		new float[] { 0.8F },
		"unload_ammo"
	);
	
	protected Set< String > allowedAmmoCategory = Collections.emptySet();
	
	@SerializedName( value = "ammoCapacity", alternate = "capacity" )
	protected int ammoCapacity = 1;
	
	protected IOperationController loadAmmoController = LOAD_AMMO_CONTROLLER;
	protected IOperationController unloadAmmoController = UNLOAD_AMMO_CONTROLLER;
	
	protected abstract class Mag extends GunPart implements IMag< I >
	{
		protected final ArrayList< IAmmoType > ammo = new ArrayList<>();
		
		@SideOnly( Side.CLIENT )
		protected transient boolean isLoadingMag;
		
		protected Mag() { }
		
		protected Mag( boolean unused ) { super( unused ); }
		
		@Override
		public boolean isFull() { return this.ammo.size() >= MagType.this.ammoCapacity; }
		
		@Override
		public int ammoCount() { return this.ammo.size(); }
		
		@Override
		public boolean isAllowed( IAmmoType ammo ) {
			return MagType.this.allowedAmmoCategory.contains( ammo.category() );
		}
		
		@Override
		public void pushAmmo( IAmmoType ammo )
		{
			this.setAmmo( this.nbt.getIntArray( DATA_TAG ), this.ammo.size(), ammo );
			this.ammo.add( ammo );
			this.syncAndUpdate(); // TODO: only sync nbt data
		}
		
		@Override
		public IAmmoType popAmmo()
		{
			final int idx = this.ammo.size() - 1;
			this.setAmmo( this.nbt.getIntArray( DATA_TAG ), idx, null );
			final IAmmoType ammo = this.ammo.remove( idx );
			this.syncAndUpdate();
			return ammo;
		}
		
		@Override
		public IAmmoType getAmmo( int idx ) { return this.ammo.get( idx ); }
		
		@Override
		@SideOnly( Side.CLIENT )
		public boolean isLoadingMag() { return this.isLoadingMag; }
		
		@Override
		@SideOnly( Side.CLIENT )
		public void setAsLoadingMag() { this.isLoadingMag = true; }
		
		@Override
		public void deserializeNBT( NBTTagCompound nbt )
		{
			super.deserializeNBT( nbt );
			
			this.ammo.clear();
			final int[] data = nbt.getIntArray( DATA_TAG );
			for ( int i = 0; i < MagType.this.ammoCapacity; ++i )
			{
				final IAmmoType ammo = this.getAmmo( data, i );
				if ( ammo == null ) { break; }
				this.ammo.add( ammo );
			}
		}
		
		@Override
		protected int dataSize() { return super.dataSize() + MagType.this.ammoCapacity / 2; }
		
		protected void setAmmo( int[] data, int idx, @Nullable IAmmoType ammo )
		{
			final int i = super.dataSize() + idx / 2;
			final int offset = idx % 2 != 0 ? 16 : 0;
			final int id = ammo != null ? Item.getIdFromItem( ammo.item() ) : 0;
			data[ i ] = data[ i ] & 0xFFFF0000 >>> offset | id << offset;
		}
		
		@Nullable
		protected IAmmoType getAmmo( int[] data, int idx )
		{
			final int i = super.dataSize() + idx / 2;
			final boolean isOddIdx = idx % 2 != 0;
			final int offset = isOddIdx ? 16 : 0;
			final int id = 0xFFFF & data[ i ] >>> offset;
			if ( id == 0 ) { return null; }
			
			final Item item = Item.getItemById( id );
			return ( IAmmoType ) ( ( IItemTypeHost ) item ).meta();
		}
		
		protected class EquippedMag extends EquippedGunPart implements IEquippedMag< C >
		{
			protected static final byte
				OP_CODE_LOAD_AMMO = 0,
				OP_CODE_UNLOAD_AMMO = 1;
			
			protected EquippedMag(
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
				case OP_CODE_LOAD_AMMO: {
					final int invSlot = buf.readByte();
					final IOperationController controller = MagType.this.loadAmmoController;
					final IOperation op = new OpLoadAmmo( this, invSlot, controller );
					PlayerPatch.get( player ).launch( op );
				break; }
					
				case OP_CODE_UNLOAD_AMMO:
					final IOperationController controller = MagType.this.unloadAmmoController;
					final IOperation op = new OpUnloadAmmo( this, controller );
					PlayerPatch.get( player ).launch( op );
				}
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public void onKeyPress( IInput key )
			{
				switch ( key.name() )
				{
				case Key.PULL_TRIGGER:
					PlayerPatchClient.instance.launch(
						new OpLoadAmmoClient( this, MagType.this.loadAmmoController )
						{
							@Override
							protected void launchCallback()
							{
								this.sendPacketToServer( new PacketNotifyItem( buf -> {
									buf.writeByte( OP_CODE_LOAD_AMMO );
									buf.writeByte( this.invSlot );
								} ) );
							}
						}
					);
					break;
					
				case Key.AIM_HOLD:
				case Key.AIM_TOGGLE:
					PlayerPatchClient.instance.launch(
						new OpUnloadAmmoClient( this, MagType.this.unloadAmmoController )
						{
							@Override
							protected void launchCallback()
							{
								this.sendPacketToServer( new PacketNotifyItem( 
									buf -> buf.writeByte( OP_CODE_UNLOAD_AMMO )
								) );
							}
						}
					);
					break;
					
//				default: super.onKeyPress( key );
				}
			}
			
			@Override
			@SideOnly( Side.CLIENT )
			public void onKeyRelease( IInput key )
			{
				switch ( key.name() )
				{
				case Key.PULL_TRIGGER:
				case Key.AIM_HOLD:
				case Key.AIM_TOGGLE:
					final IOperation executing = PlayerPatchClient.instance.executing();
					final boolean isLoadingAmmo = executing instanceof OpLoadAmmoClient;
					final boolean isUnloadingAmmo = executing instanceof OpUnloadAmmoClient;
					if ( isLoadingAmmo || isUnloadingAmmo ) {
						PlayerPatchClient.instance.ternimateExecuting();
					}
					break;
					
//				default: super.onKeyRelease( key );
				}
			}
		}
	}
}
