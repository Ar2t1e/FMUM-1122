package com.fmum.common.mag;

import com.fmum.common.ammo.IAmmoType;
import com.fmum.common.gun.GunPartWrapper;
import com.fmum.common.gun.IGunPart;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Consumer;

public class MagWrapper< I extends IGunPart< ? extends I >, T extends IMag< ? extends I > >
	extends GunPartWrapper< I, T > implements IMag< I >
{
	protected MagWrapper( T primary, ItemStack stack ) { super( primary, stack ); }
	
	@Override
	public boolean isFull() { return this.primary.isFull(); }
	
	@Override
	public int ammoCount() { return this.primary.ammoCount(); }
	
	@Override
	public boolean isAllowed( IAmmoType ammo ) { return this.primary.isAllowed( ammo ); }
	
	@Override
	public void pushAmmo( IAmmoType ammo ) { this.primary.pushAmmo( ammo ); }
	
	@Override
	public IAmmoType popAmmo() { return this.primary.popAmmo(); }
	
	@Override
	public IAmmoType peekAmmo() { return this.primary.peekAmmo(); }
	
	@Override
	public void forEachAmmo( Consumer< IAmmoType > visitor ) {
		this.primary.forEachAmmo( visitor );
	}
	
	// TODO: reformat exception
	@Override
	@SideOnly( Side.CLIENT )
	public boolean isLoadingMag() { throw new RuntimeException(); }
	
	@Override
	@SideOnly( Side.CLIENT )
	public void setAsLoadingMag() { this.primary.setAsLoadingMag(); }
}
