package com.mcwb.common.paintjob;

import com.mcwb.common.meta.IContexted;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraftforge.fml.relauncher.SideOnly;

public interface IPaintable extends IContexted
{
	public int paintjobCount();
	
	public int paintjob();
	
	public void setPaintjob( int paintjob );
	
	public boolean tryOffer( int paintjob, EntityPlayer player );
	
	@SideOnly( Side.CLIENT )
	public boolean tryOfferOrNotifyWhy( int paintjob, EntityPlayer player );
}
