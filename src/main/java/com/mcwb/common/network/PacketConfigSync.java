package com.mcwb.common.network;

import com.mcwb.client.MCWBClient;
import com.mcwb.common.MCWB;
import com.mcwb.common.ModConfig;
import com.mcwb.util.Util;

import io.netty.buffer.ByteBuf;

public final class PacketConfigSync implements IPacket
{
	@Override
	public void toBytes( ByteBuf buf )
	{
		buf.writeByte( ModConfig.maxModifyLayers );
		buf.writeByte( ModConfig.maxSlotCapacity );
		
		buf.writeFloat( ModConfig.camDropCycle );
		buf.writeFloat( ModConfig.camDropAmpl );
		buf.writeFloat( ModConfig.camDropImpact );
		buf.writeFloat( ModConfig.freeViewLimit );
	}
	
	@Override
	public void fromBytes( ByteBuf buf )
	{
		// TODO: also server side?
		MCWBClient.modifyLoc = new byte[ 2 * ( 0xFF & buf.readByte() ) ];
		MCWB.maxSlotCapacity = 0xFF & buf.readByte();
		
		/// *** Camera settings. *** ///
		MCWBClient.camDropCycle  = buf.readFloat() * Util.PI * 0.3F;
		MCWBClient.camDropAmpl   = buf.readFloat() * 3F;
		MCWBClient.camDropImpact = buf.readFloat() * 7.5F;
		
		final float limit = buf.readFloat();
		MCWBClient.freeViewLimitSquared = limit * limit;
	}
}
