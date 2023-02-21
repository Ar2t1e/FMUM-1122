package com.mcwb.client.player;

import com.mcwb.client.MCWBClient;
import com.mcwb.common.IAutowirePacketHandler;
import com.mcwb.common.gun.IGun;
import com.mcwb.common.item.IItem;
import com.mcwb.common.network.PacketCode;
import com.mcwb.common.network.PacketCode.Code;
import com.mcwb.common.operation.IOperation;
import com.mcwb.common.operation.Operation;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly( Side.CLIENT )
public class OpUnloadMagClient extends Operation< IGun > implements IAutowirePacketHandler
{
	/**
	 * This is kept for animation to use
	 */
//	protected IMag mag;
	
	public OpUnloadMagClient() { super( null, null, null ); }
	
	public IOperation reset( IGun gun )
	{
		this.player = MCWBClient.MC.player;
		this.contexted = gun;
		this.controller = gun.unloadMagController();
		return this;
	}
	
	@Override
	public IOperation launch( IOperation oldOp )
	{
		if( !this.contexted.hasMag() ) return NONE;
		
//		this.mag = this.contexted.mag();
		this.clearProgress();
		this.sendToServer( new PacketCode( Code.UNLOAD_MAG ) );
		return super.launch( oldOp );
	}
	
	@Override
	public IOperation terminate()
	{
		this.sendToServer( new PacketCode( Code.TERMINATE_OP ) );
		return NONE;
	}
	
	@Override
	public IOperation onHoldingStackChange( IItem newItem )
	{
		if( !( ( IGun ) newItem ).hasMag() )
			return this.terminate();
		
		this.contexted = ( IGun ) newItem;
		return this;
	}
	
	@Override
	protected void dohandleEffect() { this.contexted.unloadMag(); }
}
