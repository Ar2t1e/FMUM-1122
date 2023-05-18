package com.fmum.common.module;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.fmum.common.FMUM;
import com.fmum.common.module.IModuleEventSubscriber.ModuleInstallEvent;
import com.fmum.common.module.IModuleEventSubscriber.ModuleRemoveEvent;
import com.fmum.common.paintjob.IPaintable;
import com.fmum.util.Mat4f;
import com.google.common.base.Supplier;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class Module< T extends IModule< ? extends T > > implements IModule< T >, IPaintable
{
	protected static final String MODULE_TAG = "m";
	
	protected transient final Mat4f mat = new Mat4f();
	
	protected transient IModule< ? > base;
	protected transient short baseSlot;
	
	protected short paintjob = 0;
	
	protected final ArrayList< T > installed = new ArrayList<>();
	protected final byte[] indices = new byte[ this.slotCount() ];
	
	protected transient IModifyState modifyState = IModifyState.NOT_SELECTED;
	
	/**
	 * Bounden NBT used for data persistence.
	 */
	protected transient NBTTagCompound nbt;
	
	protected Module()
	{
		this.nbt = new NBTTagCompound();
		final int[] data = new int[ this.dataSize() ];
		data[ 0 ] = this.id();
		this.nbt.setIntArray( DATA_TAG, data );
		this.nbt.setTag( MODULE_TAG, new NBTTagList() );
	}
	
	/**
	 * Unfortunately calling {@link #deserializeNBT(NBTTagCompound)} could cause error as it the
	 * fields of the sub-class may not have been properly initialized. Hence it needs to be delay
	 * after the constructor finishes its work.
	 * 
	 * @param UNUSED_waitForDeserialize To distinguish this from {@link #Module()}.
	 */
	protected Module( boolean UNUSED_waitForDeserialize ) { }
	
	@Override
	public ItemStack toStack() { throw new RuntimeException(); }
	
	@Override
	public int baseSlot() { return this.baseSlot; }
	
	@Override
	public IModule< ? > base() { return this.base; }
	
	@Override
	public void setBase( IModule< ? > base, int baseSlot )
	{
		this.base = base;
		this.baseSlot = ( short ) baseSlot;
	}
	
	@Override
	public void postEvent( Object evt ) { this.base.postEvent( evt ); }
	
	@Override
	public void syncAndUpdate() { this.base.syncAndUpdate(); }
	
	@Override
	public void updateModuleState( BiConsumer< Class< ? >, IModuleEventSubscriber< ? > > registry )
	{
		// TODO: Further consider when to call mat update
		this.base.getTransform( this, this.mat );
		this.installed.forEach( mod -> mod.updateModuleState( registry ) );
	}
	
	@Override
	public IPreviewPredicate tryInstall( int islot, IModule< ? > module )
	{
		final IModuleSlot slot = this.getSlot( islot );
		if ( !slot.isAllowed( module ) ) { return IPreviewPredicate.NO_PREVIEW; }
		
		final int capacity = Math.min( FMUM.maxSlotCapacity, slot.capacity() );
		if ( this.getInstalledCount( islot ) > capacity )
		{
			final String formatter = "fmum.msg.arrive_max_module_capacity";
			return ( IPreviewPredicate.NotOk ) () -> I18n.format( formatter, capacity );
		}
		// TODO: check layer limitation
		
		final Supplier< IPreviewPredicate > action = () -> {
			// Do not delay actual installation to #index() as it may not be called by outer.
			final int idx = this.install( islot, module );
			return () -> idx;
		};
		final ModuleInstallEvent evt = new ModuleInstallEvent( this, islot, module, action );
		this.postEvent( evt );
		return evt.action.get();
	}
	
	@Override
	public IModule< ? > doRemove( int slot, int idx )
	{
		final Supplier< IModule< ? > > action = () -> this.remove( slot, idx );
		final ModuleRemoveEvent evt = new ModuleRemoveEvent( this, slot, idx, action );
		this.postEvent( evt );
		return evt.action.get();
	}
	
	@Override
	@SuppressWarnings( "unchecked" )
	public int install( final int slot, final IModule< ? > module )
	{
		final T mod = ( T ) module.onBeingInstalled();
		mod.setBase( this, slot );
		
		// Update installed list.
		final int idx = this.getIdx( slot + 1 );
		this.installed.add( idx, mod );
		
		// Update NBT tag.
		final NBTTagList modList = this.nbt.getTagList( MODULE_TAG, NBT.TAG_COMPOUND );
		final NBTTagCompound tarTag = mod.serializeNBT();
		modList.appendTag( tarTag );
		for ( int i = modList.tagCount(); --i > idx; modList.set( i, modList.get( i - 1 ) ) );
		modList.set( idx, tarTag );
		
		// Update indices.
		final int[] data = this.nbt.getIntArray( DATA_TAG );
		for ( int islot = slot; islot++ < this.indices.length; )
		{
			final int val = 1 + this.getIdx( islot );
			this.setIdx( islot, val );
			this.setIdx( data, islot, val );
		}
		this.syncAndUpdate();
		return idx - this.getIdx( slot );
	}
	
	@Override
	public IModule< ? > remove( int slot, int idx )
	{
		// Update installed list.
		final int i = this.getIdx( slot ) + idx;
		final T removed = this.installed.remove( i );
		
		// Update NBT tag.
		final NBTTagList modList = this.nbt.getTagList( MODULE_TAG, NBT.TAG_COMPOUND );
		modList.removeTag( i );
		
		// Update indices.
		final int[] data = this.nbt.getIntArray( DATA_TAG );
		while ( slot++ < this.indices.length )
		{
			final int val = -1 + this.getIdx( slot );
			this.setIdx( slot, val );
			this.setIdx( data, slot, val );
		}
		this.syncAndUpdate();
		return removed.onBeingRemoved();
	}
	
	@Override
	public IModule< ? > onBeingInstalled() { return this; }
	
	@Override
	public IModule< ? > onBeingRemoved()
	{
		final IModule< ? > wrapper = this.wrapOnBeingRemoved();
		wrapper.syncAndUpdate();
		return wrapper;
	}
	
	@Override
	public IModifyPredicate checkHitboxConflict( IModule< ? > module ) {
		throw new RuntimeException();
	}
	
	@Override
	public void forEach( Consumer< ? super T > visitor )
	{
		this.installed.forEach( mod -> {
			visitor.accept( mod );
			mod.forEach( visitor );
		} );
	}
	
	@Override
	public int getInstalledCount( int slot ) {
		return this.getIdx( slot + 1 ) - this.getIdx( slot );
	}
	
	@Override
	public T getInstalled( int slot, int idx ) {
		return this.installed.get( this.getIdx( slot ) + idx ); 
	}
	
	@Override
	public IModule< ? > getInstalled( byte[] loc, int locLen )
	{
		IModule< ? > mod = this;
		for ( int i = 0; i < locLen; i += 2 ) {
			mod = mod.getInstalled( 0xFF & loc[ i ], 0xFF & loc[ i + 1 ] );
		}
		return mod;
	}
	
	@Override
	@SuppressWarnings( "unchecked" )
	public void setInstalled( int slot, int idx, IModule< ? > module )
	{
		final T mod = ( T ) module;
		final int actualIdx = this.getIdx( slot ) + idx;
		final NBTTagList modList = this.nbt.getTagList( MODULE_TAG, NBT.TAG_COMPOUND );
		this.installed.set( actualIdx, mod );
		modList.set( actualIdx, module.serializeNBT() );
		this.syncAndUpdate();
	}
	
	@Override
	public int paintjob() { return this.paintjob; }
	
	@Override
	public void setPaintjob( int paintjob )
	{
		this.paintjob = ( short ) paintjob;
		final int[] data = this.nbt.getIntArray( DATA_TAG );
		data[ 0 ] &= 0xFFFF;
		data[ 0 ] |= paintjob << 16;
		this.syncAndUpdate();
	}
	
	@Override
	public boolean tryOffer( int paintjob, EntityPlayer player )
	{
		// TODO: validate material
		return player.capabilities.isCreativeMode;
	}
	
	@Override
	@SideOnly( Side.CLIENT )
	public boolean tryOfferOrNotifyWhy( int paintjob, EntityPlayer player )
	{
		// TODO: proper material offer
		return player.capabilities.isCreativeMode;
	}
	
	@Override
	public IModifyState modifyState() { return this.modifyState; }
	
	@Override
	public void setModifyState( IModifyState state ) { this.modifyState = state; }
	
	@Override
	public NBTTagCompound serializeNBT() { return this.nbt; }
	
	@Override
	@SuppressWarnings( "unchecked" )
	public void deserializeNBT( NBTTagCompound nbt )
	{
		// Read paintjob.
		final int data[] = nbt.getIntArray( DATA_TAG );
		this.paintjob = ( short ) ( data[ 0 ] >>> 16 );
		
		// Read install indices.
		for ( int i = this.indices.length; i > 0; --i ) {
			this.setIdx( i, this.getIdx( data, i ) );
		}
		
		// Read installed modules.
		this.installed.clear();
		final NBTTagList modList = nbt.getTagList( MODULE_TAG, NBT.TAG_COMPOUND );
		for ( int i = 0, size = modList.tagCount(), slot = 0; i < size; ++i )
		{
			final NBTTagCompound modTag = modList.getCompoundTagAt( i );
			final IModule< ? > module = this.fromTag( modTag );
			
			while ( i >= this.getIdx( slot + 1 ) ) { ++slot; }
			module.setBase( this, slot );
			this.installed.add( ( T ) module );
		}
		
		this.nbt = nbt; // Do not forget to bind to the given tag.
	}
	
	protected int dataSize() { return 1 + ( this.indices.length + 3 ) / 4; }
	
	/**
	 * @return
	 *     Id that can be used to retrieve corresponding meta with the {@link #fromId(int)} of its
	 *     base. 16 bits valid in default implementation.
	 */
	protected abstract int id();
	
	/**
	 * Used in {@link #onBeingRemoved()} to wrap this module on being removed.
	 * {@link #syncAndUpdate()} will be called on returned wrapper.
	 */
	protected abstract IModule< ? > wrapOnBeingRemoved();
	
	protected abstract IModule< ? > fromTag( NBTTagCompound tag );
	
	protected final int getIdx( int slot ) {
		return slot > 0 ? 0xFF & this.indices[ slot - 1 ] : 0;
	}
	
	protected final void setIdx( int slot, int idx ) {
		this.indices[ slot - 1 ] = ( byte ) idx;
	}
	
	// TODO: if 0 is never used then just remove it
	protected final int getIdx( int[] data, int slot )
	{
		final int islot = slot - 1;
		return slot > 0 ? 0xFF & data[ 1 + islot / 4 ] >>> ( islot % 4 ) * 8 : 0;
	}
	
	protected final void setIdx( int[] data, int slot, int val )
	{
		final int islot = slot - 1;
		final int i = 1 + islot / 4;
		final int offset = ( islot % 4 ) * 8;
		data[ i ] &= ~( 0xFF << offset );       // Clear value.
		data[ i ] |= ( 0xFF & val ) << offset;  // Set value.
	}
}
